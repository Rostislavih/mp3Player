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
shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/
└── PlaybackState.kt                          (новый, модель)

shared/data/src/
├── commonMain/kotlin/org/example/mp3player/data/player/
│   └── AudioPlayer.kt                        (новый, expect)
├── androidMain/kotlin/org/example/mp3player/data/player/
│   ├── AudioPlayer.android.kt                (новый, actual)
│   └── MusicPlaybackService.kt               (новый, Android Service)
└── iosMain/kotlin/org/example/mp3player/data/player/
    └── AudioPlayer.ios.kt                    (новый, заглушка)

composeApp/src/androidMain/AndroidManifest.xml  (+service + permission)
```

---

## Реализация

### Шаг 1 — Зависимости

`gradle/libs.versions.toml`:

```toml
[versions]
media3 = "1.7.1"

[libraries]
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
```

`shared/data/build.gradle.kts`:

```kotlin
androidMain.dependencies {
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    // ui — опционально, для готовых View-компонентов. Нам не нужно, мы на Compose.
}
```

### Шаг 2 — Разрешение + service в манифесте

`composeApp/src/androidMain/AndroidManifest.xml`:

```xml
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
            android:name="org.example.mp3player.data.player.MusicPlaybackService"
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

`foregroundServiceType="mediaPlayback"` обязателен с Android 14 (API 34).

### Шаг 3 — Модель состояния

```kotlin
// shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/PlaybackState.kt
package org.example.mp3player.domain

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffleEnabled: Boolean = false,
)

enum class RepeatMode { Off, One, All }
```

### Шаг 4 — `expect` AudioPlayer

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/player/AudioPlayer.kt
package org.example.mp3player.data.player

import kotlinx.coroutines.flow.StateFlow
import org.example.mp3player.domain.PlaybackState
import org.example.mp3player.domain.RepeatMode
import org.example.mp3player.domain.Track

expect class AudioPlayer {
    val state: StateFlow<PlaybackState>

    fun play(queue: List<Track>, startIndex: Int = 0)
    fun resume()
    fun pause()
    fun toggle()
    fun seekTo(positionMs: Long)
    fun next()
    fun previous()
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffleEnabled(enabled: Boolean)

    /** Освободить ресурсы. Вызывать при завершении приложения. */
    fun release()
}
```

### Шаг 5 — iOS заглушка

```kotlin
// shared/data/src/iosMain/kotlin/org/example/mp3player/data/player/AudioPlayer.ios.kt
package org.example.mp3player.data.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.mp3player.domain.PlaybackState
import org.example.mp3player.domain.RepeatMode
import org.example.mp3player.domain.Track

actual class AudioPlayer {
    actual val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())

    actual fun play(queue: List<Track>, startIndex: Int) { TODO("iOS: AVAudioPlayer / MPMusicPlayerController") }
    actual fun resume() { TODO("iOS") }
    actual fun pause() { TODO("iOS") }
    actual fun toggle() { TODO("iOS") }
    actual fun seekTo(positionMs: Long) { TODO("iOS") }
    actual fun next() { TODO("iOS") }
    actual fun previous() { TODO("iOS") }
    actual fun setRepeatMode(mode: RepeatMode) { TODO("iOS") }
    actual fun setShuffleEnabled(enabled: Boolean) { TODO("iOS") }
    actual fun release() {}
}
```

### Шаг 6 — `MusicPlaybackService`

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/player/MusicPlaybackService.kt
package org.example.mp3player.data.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Когда пользователь свайпнул приложение из "recents",
        // если ничего не играет — останавливаем сервис.
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

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

**Что делает этот класс?**
- Поднимает фоновую службу, в которой живёт `ExoPlayer`.
- `MediaSession` автоматически регистрирует себя в системе → появляется на экране блокировки, Bluetooth получает транспортные события, `PlayerNotificationManager` не нужен (Media3 сделает уведомление сам).
- `setHandleAudioFocus(true)` — когда кто-то другой начинает играть звук (звонок, будильник), наш плеер ставится на паузу сам.

### Шаг 7 — Android actual AudioPlayer через MediaController

`AudioPlayer` в `composeApp`/`shared:presentation` не должен запускать `ExoPlayer` напрямую — плеер живёт в сервисе. Связь между UI и сервисом — через `MediaController`:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/player/AudioPlayer.android.kt
package org.example.mp3player.data.player

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.mp3player.domain.PlaybackState
import org.example.mp3player.domain.RepeatMode
import org.example.mp3player.domain.Track

actual class AudioPlayer(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    private val _state = MutableStateFlow(PlaybackState())
    actual val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var currentQueue: List<Track> = emptyList()

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

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { syncFromController() }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { syncFromController() }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { syncFromController() }
        override fun onPlaybackStateChanged(playbackState: Int) { syncFromController() }
        override fun onPlayerError(error: PlaybackException) { /* можно пробросить в UI через State */ }
    }

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
        )
    }

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

    actual fun play(queue: List<Track>, startIndex: Int) {
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

    actual fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    actual fun release() {
        controller?.release()
        controller = null
    }

    private fun Track.toMediaItem(): MediaItem {
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

---

## Разбор

### Почему сервис + MediaController, а не ExoPlayer напрямую?

Если создать `ExoPlayer` в `ViewModel` или в `Application`, то:
- при убийстве активности музыка остановится;
- нет интеграции с экраном блокировки / Bluetooth;
- нужно самому делать foreground-уведомление.

`MediaSessionService` решает всё это. Он живёт отдельно от UI, и его поддерживает система. `MediaController` — удалённая "ручка" для управления сервисом из приложения.

#### Что такое Android `Service` и зачем он переживает Activity

`Service` — один из четырёх компонентов приложения в Android (наряду с Activity, BroadcastReceiver, ContentProvider). У него **свой жизненный цикл**, не привязанный к UI:

- `Activity` живёт пока экран открыт; при свайпе из recents может быть убита.
- `Service` (особенно foreground) живёт **пока сам не остановится** или система не убьёт под давлением памяти.

`MediaSessionService` — специализированный foreground-сервис от Media3. Кроме «не умирай»:
- автоматически создаёт уведомление о воспроизведении (с обложкой, заголовком, кнопками play/pause/next);
- регистрируется в `MediaSessionManager` системы — это даёт интеграцию с экраном блокировки, Bluetooth-кнопками, Android Auto;
- умеет принимать привязки от `MediaController` через Binder/IPC.

«Foreground» означает «у меня видимое уведомление, я важен». Android рассчитывает на это и не убивает foreground-сервис в первую очередь при нехватке памяти.

### `AudioAttributes.USAGE_MEDIA + CONTENT_TYPE_MUSIC` — что система делает с этими подсказками

```kotlin
val audioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .build()
```

Это **метаданные потока звука** для `AudioManager`. На их основе система принимает решения:

| Вопрос | Ответ для USAGE_MEDIA |
|---|---|
| Какую группу громкости использовать? | `STREAM_MUSIC` — отдельный регулятор «медиа», не «звонок» и не «уведомления» |
| Что делать при входящем звонке? | Заглушить (audio focus отдаст звонок, ducking) |
| Куда маршрутизировать в Bluetooth? | A2DP (high-quality stereo), не SCO (моно для звонков) |
| Что показать на экране блокировки? | «Сейчас играет» с обложкой, не как обычное уведомление |

Если бы поставили `USAGE_NOTIFICATION` — звук пошёл бы по группе уведомлений, был бы громкий и не паузился при звонке. Для плеера это неправильно.

`/* handleAudioFocus = */ true` — соглашение «когда другое приложение начнёт играть (звонок, навигатор, будильник), ExoPlayer сам поставит на паузу или приглушит». Без этого флага оба звука играли бы одновременно, перебивая друг друга.

### `setHandleAudioBecomingNoisy(true)`

Когда вынимаешь наушники из разъёма / отключаешь Bluetooth-колонку, Android шлёт системный `ACTION_AUDIO_BECOMING_NOISY` broadcast — буквально «звук сейчас уйдёт в обычный громкоговоритель». Без обработки — музыка продолжит играть в динамик телефона на максимальной громкости (никто не хочет такого).

ExoPlayer сам подпишется на этот broadcast и поставит паузу. Один булевый флаг — и проблема решена.

### `MediaController.Builder(...).buildAsync()` — почему именно асинхронно

```kotlin
val future = MediaController.Builder(context, sessionToken).buildAsync()
future.addListener({ controller = future.get() ... }, MoreExecutors.directExecutor())
```

Подключение к сервису — это **IPC через Binder**. Грубо говоря:

1. Наш процесс (UI) бросает `bindService(intent)`.
2. Android находит сервис, возможно стартует его (если ещё не запущен).
3. Создаётся Binder-канал между процессами.
4. Через этот канал передаётся `IBinder`-токен сессии.
5. `MediaController` оборачивает токен в удобное API.

Шаги 2-3 могут занять десятки миллисекунд. Делать это синхронно — заблокировать main-поток на это время. Поэтому `buildAsync()` возвращает `ListenableFuture<MediaController>` — это API из Guava (Google-библиотека утилит), концептуально аналог `CompletableFuture` из JDK или `Deferred` из корутин.

`future.addListener(callback, executor)` — «когда future завершится, вызови callback на этом executor». Передаём `MoreExecutors.directExecutor()` — самый ленивый executor: «не делай ничего хитрого, просто вызови callback синхронно в потоке, в котором future завершилась». Здесь это безопасно, потому что callback маленький и Media3 завершает future именно на main-потоке.

Альтернативные executor'ы: `Executors.newSingleThreadExecutor()` — отдельный поток; `mainExecutor` — главный поток через `Handler`. Но `directExecutor` подходит, когда callback не блокирующий и не критично, где он выполнится.

#### Связка с корутинами

С Media3 1.4+ есть `awaitConnect()` для использования из корутин:

```kotlin
val controller = MediaController.Builder(context, token).buildAsync().await()
```

Через extension `kotlinx.coroutines.guava.await()` (артефакт `kotlinx-coroutines-guava`). Это превращает `ListenableFuture` в suspend-точку — корутина приостанавливается до завершения future. Чище, чем listener, если у тебя уже есть scope.

### `ComponentName` и `SessionToken`

`ComponentName` — связка `packageName + className`, однозначно идентифицирующая Android-компонент:

```kotlin
ComponentName(context, MusicPlaybackService::class.java)
// эквивалент:
// ComponentName("org.example.mp3player", "org.example.mp3player.data.player.MusicPlaybackService")
```

`SessionToken` — ключ, по которому `MediaController` находит `MediaSession` в сервисе. Под капотом он содержит `ComponentName` сервиса плюс некоторые дополнительные метаданные.

Что произойдёт при несовпадении:

- Сервис лежит в `shared:data` под пакетом `org.example.mp3player.data.player.MusicPlaybackService`.
- В манифесте указано **точно то же имя**: `android:name="org.example.mp3player.data.player.MusicPlaybackService"`.
- В `SessionToken` мы передаём `MusicPlaybackService::class.java` — это даёт ровно то же `canonicalName`.

Если в манифесте имя другое (например, забыли префикс пакета `data.player.` и осталось просто `org.example.mp3player.MusicPlaybackService`) — `bindService` тихо провалится. `future.get()` бросит `SessionException`. Симптом: «контроллер не подключается, плеер не отвечает».

### `Player.Listener` — паттерн callback'ов

```kotlin
private val playerListener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) { syncFromController() }
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { syncFromController() }
    // ...
}
```

`Player.Listener` — Java-interface с **дефолтными методами** (`default void onIsPlayingChanged(boolean) {}`). Это означает: переопределяешь только нужные, остальные — пустые no-op.

`object : Player.Listener { ... }` — это **anonymous object expression**: «создай экземпляр анонимного класса, реализующего `Player.Listener`». В Kotlin это идиоматично; в Java писали бы `new Player.Listener() { @Override public void onIsPlayingChanged(...) ... }`.

Важно: callback'и `Player.Listener` вызываются **на потоке, где живёт ExoPlayer** — у нас это main. Поэтому в `syncFromController()` можно безопасно писать в `_state.value` (хотя `MutableStateFlow` thread-safe сам по себе, удобно знать, что мы на main).

### `startPositionPolling` — почему 500 мс и почему вообще polling

ExoPlayer **не эмитит** события «позиция изменилась» — это было бы 30+ событий в секунду (44.1 kHz / N samples = десятки fps), что:
- бесполезно для UI (60 fps экран — 60 апдейтов хватит, и то много);
- бьёт по батарее (бесконечная активность на main-потоке);
- бессмысленно для seek-bar (визуально не различимо).

Вместо этого мы сами опрашиваем `controller.currentPosition` периодически. 500 мс — компромисс:
- Глаз видит обновление 2 раза в секунду — seek-bar не «дёргается», но и не «замирает».
- На батарею 2 emit/сек — копейки.
- Если песня 3 минуты — 360 эмитов за песню, не страшно.

```kotlin
scope.launch {
    while (true) {
        val ctrl = controller
        if (ctrl != null && ctrl.isPlaying) {
            _state.value = _state.value.copy(positionMs = ctrl.currentPosition.coerceAtLeast(0))
        }
        delay(500)
    }
}
```

#### Почему `while(true)` не утечка

Корутина запущена в `scope`, который мы передали в `AudioPlayer` (`CoroutineScope(Dispatchers.Main)`). В `release()` мы могли бы вызвать `scope.cancel()` — все запущенные корутины умрут. На практике в нашем `AudioPlayer` явного `scope.cancel()` нет (это упрощение для гайда), но идея именно такая: scope — владелец, он же убийца.

`delay(500)` — это **корутинный sleep**, не блокирующий. На время ожидания поток main-loop'а свободен делать другую работу (рендер, ввод, всё что Compose требует). Когда 500 мс прошло — корутина просыпается, делает шаг, снова `delay`.

Ловушка, которую мы избежали: `Thread.sleep(500)` в той же позиции **заморозит** main-поток на 500 мс — UI не отреагирует на тапы, не отрисует кадр. С `delay` — никаких проблем.

### `coerceAtLeast(0)`, `takeIf { it > 0 } ?: 0` — Kotlin-идиомы для clamp

```kotlin
positionMs = ctrl.currentPosition.coerceAtLeast(0)
durationMs = ctrl.duration.takeIf { it > 0 } ?: 0
```

`coerceAtLeast(min)` = `Math.max(this, min)`. Если значение меньше `min` — вернёт `min`, иначе `this`. Зачем здесь: `currentPosition` может быть `C.TIME_UNSET` (= `Long.MIN_VALUE`) сразу после `prepare()`, до начала воспроизведения. Без clamp seek-bar показал бы отрицательную позицию.

Парный `coerceAtMost(max)` и общий `coerceIn(min, max)` — для двусторонних зажимов.

`takeIf { предикат }` возвращает `this`, если предикат истинен, иначе `null`. Удобно в цепочках:

```kotlin
ctrl.duration.takeIf { it > 0 } ?: 0
// эквивалентно:
if (ctrl.duration > 0) ctrl.duration else 0
```

Идиоматичный «если значение бессмысленное — заменить на дефолт». Для длительности 0 / -1 / `TIME_UNSET` — все они «не известно», и мы их сводим к 0.

### `mediaSession?.run { ... }` vs `let`/`apply`/`also`

Kotlin даёт четыре scope-функции, отличающиеся двумя характеристиками:

| Функция | Как доступен объект внутри | Что возвращает |
|---|---|---|
| `let { it -> ... }` | через `it` (или явное имя) | результат блока |
| `run { ... }` | через `this` (неявно) | результат блока |
| `apply { ... }` | через `this` (неявно) | сам объект (this) |
| `also { it -> ... }` | через `it` | сам объект (this) |

`mediaSession?.run { player.release(); release(); mediaSession = null }`:
- `?.` — выполнить только если не null.
- `run` — внутри блока `this` это сам `mediaSession`, поэтому `player.release()` это `this.player.release()` (т.е. `mediaSession.player.release()`), а второй `release()` — `mediaSession.release()`.
- Возвращаемое значение нас не интересует.

Альтернатива через `let`:

```kotlin
mediaSession?.let { ms ->
    ms.player.release()
    ms.release()
    mediaSession = null
}
```

Чуть длиннее, но иногда читабельнее (явное имя). Какую брать — стилистика; в `onDestroy` `run` уместен, потому что блок короткий.

### `Dispatchers.Main` для контроллера — обязательно

```kotlin
private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
```

ExoPlayer / `MediaController` — **single-threaded**, привязаны к `Looper`, на котором их создали. Если в `MediaController.Builder(context, token).buildAsync()` мы оказались в main — все вызовы `controller.play()`, `controller.seekTo()` и доступ к `currentPosition` должны идти с main.

Любая попытка изнутри `Dispatchers.IO` или `Default` дёрнуть `controller.play()` → `IllegalStateException: Player is accessed on the wrong thread`. Поэтому `scope = CoroutineScope(Dispatchers.Main)` в `AudioPlayer` — намеренно: всё, что мы делаем с контроллером (включая polling-цикл), идёт с main.

`MutableStateFlow.value =` — thread-safe сам по себе, его можно вызывать с любого потока. Но в нашем случае мы и так на main, так что не имеет значения.

### `onTaskRemoved`

Когда свайпаешь приложение из recents, но музыка играет — пользователь не хочет, чтобы она умерла. `onTaskRemoved` даёт решить: играет → не трогаем, стоит → стопим.

Что важно про этот callback:
- Вызывается **только** при swipe-from-recents, не при home button и не при destroy Activity.
- Запускается в main-потоке сервиса.
- К этому моменту Activity уже умерла, но сервис — пока ещё живой.

Логика проверки `!playWhenReady || mediaItemCount == 0` — если плеер не собирается играть (юзер поставил на паузу и закрыл) или очередь пуста — нет смысла держать сервис, останавливаем `stopSelf()`. Если играет — `onTaskRemoved` ничего не делает, сервис продолжает жить.

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
Если сервис лежит в `shared:data`, а класс пишешь как `com.example...` — ComponentName не находит → `MediaController` висит вечно.

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
