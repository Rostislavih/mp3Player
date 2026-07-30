# 04. Воспроизведение — Media3 / ExoPlayer

## Зачем

Классический `android.media.MediaPlayer` в 2026 году — legacy. У него:
- нет нормальной буферизации,
- нет гапплесс-воспроизведения (переход между треками с паузой),
- нет абстракции MediaSession "из коробки",
- непредсказуемое поведение при смене аудио-фокуса.

**Media3** (обёртка над ExoPlayer) — актуальный стек от Google:
- ExoPlayer для самого воспроизведения,
- `MediaSession` — связь с системой (экран блокировки, Bluetooth, Android Auto, Wear OS),
- `MediaSessionService` — фоновая служба, в которой живёт плеер,
- `PlayerNotificationManager` — автоматическое уведомление.

Один раз настроил — получил поддержку всех системных интерфейсов бесплатно.

---

## Что реализуем

1. Подключим Media3.
2. Объявим `expect class AudioPlayer` в `commonMain` + Android-реализацию + iOS-заглушку.
3. Напишем `MusicPlaybackService: MediaSessionService`, в котором живёт `ExoPlayer`.
4. Зарегистрируем сервис в `AndroidManifest`.
5. Свяжем `AudioPlayer` с `MusicPlaybackService` через `MediaController`.
6. Выставим `StateFlow<PlaybackState>` для UI.

Новые файлы:

```
core/src/
├── commonMain/kotlin/org/example/mp3player/core/audio/player/
│   ├── PlaybackState.kt                      (новый, модель)
│   ├── RepeatMode.kt                         (новый, enum)
│   └── AudioPlayer.kt                        (новый, expect)
├── androidMain/kotlin/org/example/mp3player/core/audio/player/
│   ├── AudioPlayer.android.kt                (новый, actual)
│   └── MusicPlaybackService.kt               (новый, Android Service)
└── iosMain/kotlin/org/example/mp3player/core/audio/player/
    └── AudioPlayer.ios.kt                    (новый, заглушка)

composeApp/src/androidMain/AndroidManifest.xml  (+service + permission)
```

Всё в `:core` рядом с `AudioTrack` из главы 02 — плеер и модель трека неразделимы, и оба нужны всем слоям выше.

---

## Реализация

### Шаг 1 — Зависимости

Media3 — это набор артефактов. Нам нужны три: `exoplayer` (само воспроизведение), `session` (`MediaSession`/`MediaController` для интеграции с системой) и `ui` (готовые View-компоненты — не используем, мы на Compose).

Добавляем версии и артефакты:

```toml
# gradle/libs.versions.toml
[versions]
media3 = "1.7.1"

[libraries]
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
```

Подключаем в Android-таргет:

```kotlin
// core/build.gradle.kts
androidMain.dependencies {
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    // ui — опционально, для готовых View-компонентов. Нам не нужно, мы на Compose.
}
```

Media3 — Android-only, поэтому только `androidMain`.

### Шаг 2 — Разрешение + service в манифесте

Чтобы плеер работал в фоне, нужны три permission'а и объявление `<service>` в манифесте. Открываем `AndroidManifest.xml` и дополняем:

```xml
<!-- composeApp/src/androidMain/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Существующие permission из 02 -->
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

    <!-- Новое: foreground service для фонового воспроизведения -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <!-- Для показа уведомлений на Android 13+ -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application ...>

        <service
            android:name="org.example.mp3player.core.audio.player.MusicPlaybackService"
            android:exported="true"
            android:foregroundServiceType="mediaPlayback">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

`android:exported="true"` обязательно — иначе система не сможет привязаться к сервису для управления с экрана блокировки / Bluetooth.

`foregroundServiceType="mediaPlayback"` обязателен с Android 14 (API 34). Без него foreground-сервис упадёт при старте на новых устройствах.

`<intent-filter>` с `action="androidx.media3.session.MediaSessionService"` — обязательный маркер, через который `MediaController` находит сервис.

### Шаг 3 — Модель состояния

Доменная модель того, «что сейчас играет». Один data class — снимок состояния плеера в каждый момент. Этот же `PlaybackState` будет ходить через `StateFlow` в UI.

```kotlin
// core/src/commonMain/kotlin/org/example/mp3player/core/audio/player/PlaybackState.kt
package org.example.mp3player.core.audio.player

data class PlaybackState(
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<AudioTrack> = emptyList(),
    val queueIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffleEnabled: Boolean = false,
)
```

Отдельным файлом — режим повтора:

```kotlin
// core/src/commonMain/kotlin/org/example/mp3player/core/audio/player/RepeatMode.kt
package org.example.mp3player.core.audio.player

enum class RepeatMode { Off, One, All }
```

Все поля с дефолтами — это «пустое» состояние при старте приложения, когда плеер ещё не подключен. `queueIndex = -1` означает «ничего не выбрано» (в отличие от `0` — «первый трек»).

`RepeatMode` — enum: три константных состояния без своих полей. Сравни с `sealed interface`, который нужен, когда у вариантов разные данные (см. 02 Шаг 10 про выбор enum vs sealed, и 06 Часть 1 про выбор формы `UiState`).

### Шаг 4 — `expect` AudioPlayer

KMP-механика та же, что в 02 (см. Шаг 4 там): `expect class` в `commonMain`, реальная реализация в `androidMain`, заглушка в `iosMain`.

```kotlin
// core/src/commonMain/kotlin/org/example/mp3player/core/audio/player/AudioPlayer.kt
package org.example.mp3player.core.audio.player

import kotlinx.coroutines.flow.StateFlow

expect class AudioPlayer {
    val state: StateFlow<PlaybackState>

    fun play(queue: List<AudioTrack>, startIndex: Int = 0)
    fun resume()
    fun pause()
    fun toggle()
    fun seekTo(positionMs: Long)
    fun next()
    fun previous()
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffleModeEnabled(enabled: Boolean)

    /** Освободить ресурсы. Вызывать при завершении приложения. */
    fun release()
}
```

Все методы НЕ-suspend — потому что под капотом они вызывают `MediaController`, который сам ничего не блокирует (отправляет IPC-команду в сервис и сразу возвращается). Реактивные обновления приходят через `state: StateFlow<PlaybackState>`.

### Шаг 5 — iOS заглушка

Чтобы `commonMain` собирался для iOS-таргета, `actual class` нужен и там. На iOS будет реализация через `AVAudioPlayer`/`MPMusicPlayerController`, но мы её не пишем — кладём заглушку с `TODO`:

```kotlin
// core/src/iosMain/kotlin/org/example/mp3player/core/audio/player/AudioPlayer.ios.kt
package org.example.mp3player.core.audio.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class AudioPlayer {
    actual val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())

    actual fun play(queue: List<AudioTrack>, startIndex: Int) { TODO("iOS: AVAudioPlayer / MPMusicPlayerController") }
    actual fun resume() { TODO("iOS") }
    actual fun pause() { TODO("iOS") }
    actual fun toggle() { TODO("iOS") }
    actual fun seekTo(positionMs: Long) { TODO("iOS") }
    actual fun next() { TODO("iOS") }
    actual fun previous() { TODO("iOS") }
    actual fun setRepeatMode(mode: RepeatMode) { TODO("iOS") }
    actual fun setShuffleModeEnabled(enabled: Boolean) { TODO("iOS") }
    actual fun release() {}
}
```

`state` инициализирован пустым `MutableStateFlow(PlaybackState())` — чтобы код, который подписывается, не падал даже на iOS. `release()` — пустой, потому что освобождать пока нечего.

### Шаг 6 — `MusicPlaybackService`

**Почему сервис + MediaController, а не ExoPlayer напрямую.** Если создать `ExoPlayer` в `ViewModel` или в `Application`, то при убийстве активности музыка остановится, нет интеграции с экраном блокировки / Bluetooth, нужно самому делать foreground-уведомление. `MediaSessionService` решает всё это: он живёт отдельно от UI, его поддерживает система. `MediaController` — удалённая «ручка» для управления сервисом из приложения.

**Что такое Android `Service` и зачем он переживает Activity.** `Service` — один из четырёх компонентов приложения в Android (наряду с `Activity`, `BroadcastReceiver`, `ContentProvider`). У него **свой жизненный цикл**, не привязанный к UI:
- `Activity` живёт пока экран открыт; при свайпе из recents может быть убита.
- `Service` (особенно foreground) живёт **пока сам не остановится** или система не убьёт под давлением памяти.

`MediaSessionService` — специализированный foreground-сервис от Media3. Кроме «не умирай»:
- автоматически создаёт уведомление о воспроизведении (с обложкой, заголовком, кнопками play/pause/next);
- регистрируется в `MediaSessionManager` системы — это даёт интеграцию с экраном блокировки, Bluetooth-кнопками, Android Auto;
- умеет принимать привязки от `MediaController` через Binder/IPC.

«Foreground» означает «у меня видимое уведомление, я важен». Android не убивает foreground-сервис в первую очередь при нехватке памяти.

Создаём файл — пакет, импорты, класс наследует `MediaSessionService`, поле для сессии:

```kotlin
// core/src/androidMain/kotlin/org/example/mp3player/core/audio/player/MusicPlaybackService.kt
package org.example.mp3player.core.audio.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // дальше — onCreate (создание ExoPlayer и MediaSession), onGetSession (доступ извне), onTaskRemoved (свайп recents), onDestroy (освобождение)
}
```

`mediaSession: MediaSession?` — `var` и nullable, потому что время жизни этого поля привязано к `onCreate`/`onDestroy` сервиса, а не к экземпляру класса. До `onCreate` его нет; после `onDestroy` мы выставляем `null`.

В `onCreate` собираем плеер. Сначала — `AudioAttributes` (метаданные звука для системы):

```kotlin
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // дальше — ExoPlayer.Builder + MediaSession.Builder + присвоение в mediaSession
    }

    // onGetSession, onTaskRemoved, onDestroy — дальше
}
```

`AudioAttributes` — это **метаданные потока звука** для `AudioManager`. На их основе система принимает решения:

| Вопрос | Ответ для `USAGE_MEDIA` |
|---|---|
| Какую группу громкости использовать? | `STREAM_MUSIC` — отдельный регулятор «медиа», не «звонок» и не «уведомления» |
| Что делать при входящем звонке? | Заглушить (audio focus отдаст звонок, ducking) |
| Куда маршрутизировать в Bluetooth? | A2DP (high-quality stereo), не SCO (моно для звонков) |
| Что показать на экране блокировки? | «Сейчас играет» с обложкой, не как обычное уведомление |

Если бы поставили `USAGE_NOTIFICATION` — звук пошёл бы по группе уведомлений, был бы громкий и не паузился при звонке. Для плеера это неправильно.

Дальше — `ExoPlayer` + `MediaSession`:

```kotlin
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            // Правильная обработка аудио-фокуса (пауза при звонке и т.п.)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            // При отключении наушников — пауза.
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }
```

`/* handleAudioFocus = */ true` — соглашение «когда другое приложение начнёт играть (звонок, навигатор, будильник), ExoPlayer сам поставит на паузу или приглушит». Без этого флага оба звука играли бы одновременно.

`setHandleAudioBecomingNoisy(true)` — когда вынимаешь наушники / отключаешь Bluetooth-колонку, Android шлёт системный `ACTION_AUDIO_BECOMING_NOISY` broadcast — «звук сейчас уйдёт в обычный громкоговоритель». Без обработки музыка продолжит играть в динамик телефона на максимальной громкости. ExoPlayer сам подпишется на этот broadcast и поставит паузу. Один булевый флаг — и проблема решена.

`MediaSession.Builder(this, exoPlayer).build()` — оборачивает плеер в сессию. С этого момента система видит наше воспроизведение: экран блокировки, Bluetooth, Android Auto автоматически подцепляются.

Дальше — точка доступа извне через `onGetSession`. Сюда система обращается, когда какой-то контроллер хочет подключиться:

```kotlin
class MusicPlaybackService : MediaSessionService() {

    // ... onCreate выше ...

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    // onTaskRemoved, onDestroy — дальше
}
```

Просто отдаём существующую сессию. Тут можно было бы фильтровать по `controllerInfo` (например, отказать ненадёжным контроллерам), но в учебном проекте такой защиты не нужно.

Обработка свайпа приложения из recents:

```kotlin
class MusicPlaybackService : MediaSessionService() {

    // ... onCreate, onGetSession выше ...

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Когда пользователь свайпнул приложение из "recents",
        // если ничего не играет — останавливаем сервис.
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    // onDestroy — дальше
}
```

`onTaskRemoved` вызывается **только** при swipe-from-recents — не при нажатии Home и не при destroy Activity. К этому моменту Activity уже умерла, но сервис ещё жив. Логика: если плеер не играет или очередь пуста — нет смысла держать сервис, `stopSelf()`. Если играет — ничего не делаем, музыка продолжит играть в фоне.

Без этого callback'а сервис висел бы вечно даже если пользователь свернул и забыл приложение. С ним — поведение интуитивное.

И последний lifecycle-метод — освобождение ресурсов:

```kotlin
class MusicPlaybackService : MediaSessionService() {

    // ... все методы выше ...

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
```

`mediaSession?.run { ... }` — две Kotlin-идиомы в одной строке. `?.` — выполнить только если не null. `run` — внутри блока `this` это сам `mediaSession`, поэтому `player.release()` это `mediaSession.player.release()`, а второй `release()` — `mediaSession.release()`.

Kotlin даёт четыре scope-функции, отличающиеся двумя характеристиками:

| Функция | Как доступен объект внутри | Что возвращает |
|---|---|---|
| `let { it -> ... }` | через `it` (или явное имя) | результат блока |
| `run { ... }` | через `this` (неявно) | результат блока |
| `apply { ... }` | через `this` (неявно) | сам объект (this) |
| `also { it -> ... }` | через `it` | сам объект (this) |

Альтернатива через `let`:

```kotlin
mediaSession?.let { ms ->
    ms.player.release()
    ms.release()
    mediaSession = null
}
```

Чуть длиннее, но иногда читабельнее (явное имя). Здесь `run` уместен — блок короткий.

### Шаг 7 — Android actual AudioPlayer через MediaController

`AudioPlayer` в `composeApp`/`shared:presentation` не должен запускать `ExoPlayer` напрямую — плеер живёт в сервисе. Связь между UI и сервисом — через `MediaController`. По методам класс получится большой, поэтому собираем его поэтапно.

Создаём файл — пакет, импорты, класс с конструктором, state, controller, init:

```kotlin
// core/src/androidMain/kotlin/org/example/mp3player/core/audio/player/AudioPlayer.android.kt
package org.example.mp3player.core.audio.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

actual class AudioPlayer(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    private val _state = MutableStateFlow(PlaybackState())
    actual val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var currentQueue: List<AudioTrack> = emptyList()

    init { connect() }

    // дальше — connect() для bind к сервису, playerListener, syncFromController(), startPositionPolling(), и action-методы play/resume/pause/etc
}
```

`scope: CoroutineScope = CoroutineScope(Dispatchers.Main)` — намеренно. ExoPlayer и `MediaController` — **single-threaded**, привязаны к `Looper`, на котором их создали. Любой вызов из `Dispatchers.IO` или `Default` к `controller.play()` → `IllegalStateException: Player is accessed on the wrong thread`. Поэтому всё, что мы делаем с контроллером (включая polling-цикл), идёт с main.

`_state` приватный мутабельный, наружу через `state` (read-only, через `asStateFlow()`). Тот же паттерн инкапсуляции, что в Шаге 7 файла 02.

`init { connect() }` — при создании `AudioPlayer` сразу инициируем подключение к сервису.

Реализация `connect()` — bind к `MusicPlaybackService` через `MediaController`:

```kotlin
actual class AudioPlayer(...) {

    // поля выше

    init { connect() }

    private fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java),
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(playerListener)
                syncFromController(c)
            }
            startPositionPolling()
        }, MoreExecutors.directExecutor())
    }

    // дальше — playerListener (object : Player.Listener), syncFromController(), startPositionPolling(), action-методы
}
```

**`ComponentName` и `SessionToken`.** `ComponentName` — связка `packageName + className`, однозначно идентифицирующая Android-компонент. `ComponentName(context, MusicPlaybackService::class.java)` эквивалентно `ComponentName("org.example.mp3player", "org.example.mp3player.core.audio.player.MusicPlaybackService")`. `SessionToken` — ключ, по которому `MediaController` находит `MediaSession` в сервисе; внутри содержит этот `ComponentName` плюс метаданные.

Что произойдёт при несовпадении имени класса в манифесте и в `ComponentName`: `bindService` тихо провалится, `future.get()` бросит `SessionException`. Симптом: «контроллер не подключается, плеер не отвечает». Поэтому проверь, что в манифесте `android:name="org.example.mp3player.core.audio.player.MusicPlaybackService"` совпадает с фактическим путём класса.

**`MediaController.Builder(...).buildAsync()` — почему асинхронно.** Подключение к сервису — это **IPC через Binder**:

1. Наш процесс (UI) бросает `bindService(intent)`.
2. Android находит сервис, возможно стартует его (если ещё не запущен).
3. Создаётся Binder-канал между процессами.
4. Через канал передаётся `IBinder`-токен сессии.
5. `MediaController` оборачивает токен в удобное API.

Шаги 2-3 могут занять десятки миллисекунд. Делать это синхронно — заблокировать main-поток. Поэтому `buildAsync()` возвращает `ListenableFuture<MediaController>` — это API из Guava (Google-библиотека утилит), концептуально аналог `CompletableFuture` из JDK или `Deferred` из корутин.

`future.addListener(callback, executor)` — «когда future завершится, вызови callback на этом executor». `MoreExecutors.directExecutor()` — самый ленивый executor: «не делай ничего хитрого, просто вызови callback синхронно в потоке, где future завершилась». Здесь безопасно — callback маленький, Media3 завершает future на main-потоке.

С Media3 1.4+ есть и корутинный вариант — `await()` из `kotlinx-coroutines-guava`:

```kotlin
val controller = MediaController.Builder(context, token).buildAsync().await()
```

Чище, если у тебя уже есть scope. Мы остались на listener-варианте для совместимости.

Дальше — listener плеера, чтобы реагировать на изменения состояния:

```kotlin
actual class AudioPlayer(...) {

    // поля + init + connect() выше

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { syncFromController() }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { syncFromController() }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { syncFromController() }
        override fun onPlaybackStateChanged(playbackState: Int) { syncFromController() }
        override fun onPlayerError(error: PlaybackException) { /* можно пробросить в UI через State */ }
    }

    // дальше — syncFromController(), startPositionPolling(), action-методы
}
```

`Player.Listener` — Java-interface с **дефолтными методами** (`default void onIsPlayingChanged(boolean) {}`). Это означает: переопределяешь только нужные, остальные — пустые no-op.

`object : Player.Listener { ... }` — **anonymous object expression**: «создай экземпляр анонимного класса, реализующего `Player.Listener`». В Kotlin это идиоматично; в Java писали бы `new Player.Listener() { ... }`.

Callback'и `Player.Listener` вызываются **на потоке, где живёт ExoPlayer** — у нас это main. Поэтому в `syncFromController()` безопасно писать в `_state.value` (хотя `MutableStateFlow` thread-safe сам по себе, удобно знать, что мы на main).

Сама функция синхронизации — собирает свежий снимок `PlaybackState` из контроллера:

```kotlin
actual class AudioPlayer(...) {

    // ... выше: поля, init, connect, playerListener

    private fun syncFromController(c: MediaController? = controller) {
        val ctrl = c ?: return
        val index = ctrl.currentMediaItemIndex
        val current = currentQueue.getOrNull(index)
        _state.value = _state.value.copy(
            currentTrack = current,
            isPlaying = ctrl.isPlaying,
            positionMs = ctrl.currentPosition.coerceAtLeast(0),
            durationMs = ctrl.duration.takeIf { it > 0 } ?: 0,
            queue = currentQueue,
            queueIndex = index,
            repeatMode = when (ctrl.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                else -> RepeatMode.Off
            },
            shuffleEnabled = ctrl.shuffleModeEnabled,
        )
    }

    // дальше — startPositionPolling() и action-методы
}
```

**`repeatMode` и `shuffleEnabled` читаем здесь же — иначе они «залипнут».** Поля есть в `PlaybackState` (Шаг 3), и легко решить, что достаточно их записать в `setRepeatMode()`/`setShuffleModeEnabled()`. Но тогда состояние разъедется с реальностью: режим повтора можно переключить и **снаружи приложения** — с экрана блокировки, из шторки уведомления, с гарнитуры. Единственный честный источник правды — сам `MediaController`, и `syncFromController` вызывается на каждое изменение через `playerListener`.

Обратный маппинг `Player.REPEAT_MODE_* → RepeatMode` — зеркало того, что делает `setRepeatMode()` (см. ниже). `else -> RepeatMode.Off` вместо перечисления `REPEAT_MODE_OFF`: `ctrl.repeatMode` это обычный `Int`, компилятор не может проверить исчерпывающесть, а неизвестное значение разумнее трактовать как «без повтора», чем ронять приложение.

**`coerceAtLeast(0)`, `takeIf { it > 0 } ?: 0` — Kotlin-идиомы для clamp.**

`coerceAtLeast(min)` = `Math.max(this, min)`. Если значение меньше `min` — вернёт `min`, иначе `this`. Зачем здесь: `currentPosition` может быть `C.TIME_UNSET` (= `Long.MIN_VALUE`) сразу после `prepare()`, до начала воспроизведения. Без clamp seek-bar показал бы отрицательную позицию.

Парный `coerceAtMost(max)` и общий `coerceIn(min, max)` — для двусторонних зажимов.

`takeIf { предикат }` возвращает `this`, если предикат истинен, иначе `null`. Удобно в цепочках:

```kotlin
ctrl.duration.takeIf { it > 0 } ?: 0
// эквивалентно:
if (ctrl.duration > 0) ctrl.duration else 0
```

Идиоматичный «если значение бессмысленное — заменить на дефолт». Длительность 0 / -1 / `TIME_UNSET` — все они «не известно», сводим к 0.

Polling-цикл для позиции (плеер не эмитит её сам — мы опрашиваем):

```kotlin
actual class AudioPlayer(...) {

    // ... всё выше ...

    private fun startPositionPolling() {
        // Плеер не эмитит тик за тиком — полим позицию, пока играет.
        scope.launch {
            while (true) {
                val ctrl = controller
                if (ctrl != null && ctrl.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = ctrl.currentPosition.coerceAtLeast(0)
                    )
                }
                delay(500)
            }
        }
    }

    // дальше — action-методы play/resume/pause/toggle/seekTo/next/previous/setRepeatMode/setShuffleModeEnabled/release и toMediaItem
}
```

ExoPlayer **не эмитит** события «позиция изменилась» — это было бы 30+ событий в секунду (44.1 kHz / N samples = десятки fps), что бесполезно для UI (60 fps — 60 апдейтов хватит), бьёт по батарее и бессмысленно визуально.

Мы сами опрашиваем `controller.currentPosition` периодически. 500 мс — компромисс: глаз видит обновление 2 раза в секунду (seek-bar не «дёргается»), на батарею 2 emit/сек — копейки.

**Почему `while(true)` не утечка.** Корутина запущена в `scope`, который мы передали в конструктор (`CoroutineScope(Dispatchers.Main)`). В `release()` мы зовём `scope.cancel()` — цикл прерывается на ближайшем `delay(500)`, корутина умирает. Идея: scope — владелец, он же убийца.

Не пропусти `scope.cancel()` в `release()` (код ниже) — без него `while (true)` продолжит крутиться и дёргать `_state` уже после того, как плеер отпущен. Оговорка на будущее: раз `release()` отменяет scope, `AudioPlayer` после него **одноразовый** — заново подключиться не получится. Для нас это нормально (он `single` в Koin и живёт всё приложение), но если решишь пересоздавать плеер — либо не передавай в конструктор чужой scope, либо создавай новый в `connect()`.

`delay(500)` — это **корутинный sleep**, не блокирующий. На время ожидания поток main-loop'а свободен делать другую работу (рендер, ввод). Когда 500 мс прошло — корутина просыпается, делает шаг, снова `delay`. Сравни с `Thread.sleep(500)` в той же позиции — это **заморозит** main на 500 мс. С `delay` — никаких проблем.

И action-методы — `play` (запуск очереди), плюс простые делегаты на `MediaController`:

```kotlin
actual class AudioPlayer(...) {

    // ... всё выше ...

    actual fun play(queue: List<AudioTrack>, startIndex: Int) {
        val ctrl = controller ?: return
        currentQueue = queue
        val items = queue.map { it.toMediaItem() }
        ctrl.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0)
        ctrl.prepare()
        ctrl.play()
    }

    actual fun resume() { controller?.play() }
    actual fun pause() { controller?.pause() }

    actual fun toggle() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    actual fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }
    actual fun next() { controller?.seekToNextMediaItem() }
    actual fun previous() { controller?.seekToPreviousMediaItem() }

    actual fun setRepeatMode(mode: RepeatMode) {
        controller?.repeatMode = when (mode) {
            RepeatMode.Off -> Player.REPEAT_MODE_OFF
            RepeatMode.One -> Player.REPEAT_MODE_ONE
            RepeatMode.All -> Player.REPEAT_MODE_ALL
        }
    }

    actual fun setShuffleModeEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    actual fun release() {
        scope.cancel()
        controller?.release()
        controller = null
    }

    // дальше — private fun AudioTrack.toMediaItem() для конвертации в Media3-модель
}
```

`play()` сохраняет очередь в `currentQueue` (нужно для `syncFromController`, чтобы по индексу из контроллера достать `AudioTrack`), конвертирует треки в `MediaItem`, и стартует. `startIndex.coerceIn(0, items.size - 1)` страхует от out-of-range (например, если очередь укоротилась).

Остальные методы — тонкие обёртки вокруг `controller.*`. `?.` спасает от NPE, если контроллер ещё не подключился.

`setRepeatMode` — маппинг доменного `RepeatMode` enum в Media3-константы. Это типичная задача интеграции: domain-слой не должен знать про `Player.REPEAT_MODE_OFF`, поэтому маппинг живёт в data-слое.

И финал — приватный extension для конвертации трека в `MediaItem`:

```kotlin
actual class AudioPlayer(...) {

    // ... все методы выше ...

    private fun AudioTrack.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(coverUri?.let(Uri::parse))
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(path)
            .setMediaMetadata(metadata)
            .build()
    }
}
```

`MediaItem` — это «трек глазами Media3»: URI, метаданные, опциональный `mediaId`. ExoPlayer проигрывает `MediaItem`, поэтому домашний `AudioTrack` нужно сконвертировать.

`MediaMetadata` важна для системы: её **читает экран блокировки и Bluetooth**. Без `setTitle`/`setArtist` — на экране блокировки будет пусто. `setArtworkUri` — обложка из `coverUri` через `Uri.parse`.

`coverUri?.let(Uri::parse)` — это `coverUri?.let { Uri.parse(it) }`, но короче: `Uri::parse` — member-reference, экспрешн «возьми ссылку на метод `Uri.parse(String)`». `let` подставляет туда `it`. Эквивалентно `if (coverUri != null) Uri.parse(coverUri) else null`.

---

## Подводные камни

### 1. Забыт `foregroundServiceType` в манифесте
На Android 14+ — краш при старте foreground-сервиса. Всегда `android:foregroundServiceType="mediaPlayback"`.

### 2. `android:exported="false"`
Система не подключится к сервису → блокировка/Bluetooth не работают. Должно быть `true`.

### 3. Нет `intent-filter` с action `MediaSessionService`
Без него `MediaController` не найдёт сессию. Обязателен.

### 4. POST_NOTIFICATIONS не запрошено
На Android 13+ — уведомление плеера не покажется. Надо `ActivityResultContracts.RequestPermission` для `android.permission.POST_NOTIFICATIONS`. Добавь в `MainActivity` при первом старте.

### 5. Несовпадение пакета/класса сервиса
Если сервис лежит в `:core` с пакетом `org.example.mp3player.core.audio.player`, а в манифесте написано `com.example...` — `ComponentName` не находит класс, и `MediaController` висит вечно. Имя в манифесте должно совпадать с реальным пакетом байт-в-байт.

### 6. `ExoPlayer` создан в `Main`, используется в `IO`
ExoPlayer требует **всегда одного и того же Looper**. По умолчанию — `Main`. Любой вызов из фонового потока бросит `IllegalStateException`. Все методы `controller.*` — только из главного потока. В `AudioPlayer` scope = `Dispatchers.Main` не случайно.

### 7. `MediaController` не освобождается
Каждый `buildAsync()` = подписка. Если забыть `release()` — утечка и лишние события. Вызывай `release()` из ViewModel.onCleared (в presentation).

### 8. Релизы Media3
API Media3 активно меняется. Примеры из более старых туториалов могут не собраться. Держись версии из `libs.versions.toml` и ReleaseNotes AndroidX Media.

---

## Try yourself

1. **Запусти сервис**: запусти приложение, в `MainActivity.onCreate` вызови `startService(Intent(this, MusicPlaybackService::class.java))`. В Logcat отфильтруй по "MusicPlaybackService" — увидишь `onCreate`.

2. **Проверь play**: в `TracksScreen` добавь кнопку `Button(onClick = { audioPlayer.play(tracks, 0) })`. Должен заиграть первый трек, на экране блокировки появится контрол.

3. **Проверь фон**: начни воспроизведение, сверни приложение (Home). Музыка должна продолжить играть.

4. **Проверь "свайп из recents"**: сверни, открой recents, свайпни карточку приложения. Если играет — должна продолжить; если нет — сервис остановится. Это из `onTaskRemoved`.

5. **Добавь скорость воспроизведения**: метод `setSpeed(speed: Float)` → `controller?.setPlaybackSpeed(speed)`. В UI Slider 0.5..2.0.

6. **Посмотри события**: подпишись в `PlayerViewModel` на `audioPlayer.state` через `collect` и логируй переходы.

---

## Дальше

→ [`05-DI_KOIN.md`](./05-DI_KOIN.md)

## Ссылки

- [Media3 — Getting started](https://developer.android.com/media/media3)
- [MediaSessionService](https://developer.android.com/media/media3/session/background-playback)
- [MediaController](https://developer.android.com/reference/androidx/media3/session/MediaController)
- [Foreground service types — Android 14](https://developer.android.com/about/versions/14/changes/fgs-types-required)
