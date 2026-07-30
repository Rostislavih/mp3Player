# 01. Архитектура проекта

## Зачем

Хорошая архитектура — это не "так принято в книжках". Это ответ на три конкретных вопроса, которые возникнут в любом нетривиальном приложении:

1. **Где код, который читает файлы?** Если ответ "в Composable" — при переходе на iOS всё придётся переписать, а тесты невозможны.
2. **Как экран узнаёт о новых треках?** Если экран напрямую дёргает `MusicScanner` — он становится зависим от Android, от корутин, от потоков ввода-вывода. Тестировать UI придётся на устройстве.
3. **Что изменится, если я заменю MediaStore на ручное сканирование?** В идеале — один класс в `data`. Не UI, не ViewModel, не `domain`.

Clean Architecture даёт ответы: каждая ответственность живёт в своём слое. **Это не "шаблон на всякий случай"** — это конкретное разделение, 
которое избавляет от боли при росте проекта.

---

## Что реализуем

В этом файле кода почти нет — только структура. Но к концу ты поймёшь:

- зачем три модуля `shared/data`, `shared/domain`, `shared/presentation`;
- как они видят друг друга в Gradle;
- что такое `expect`/`actual` и почему мы это делаем даже в "только Android" проекте;
- как данные текут через слои от нажатия кнопки до обновления UI.

---

## Слои Clean Architecture

Представь, что приложение — многослойный пирог. Верхний слой знает про все слои ниже. Нижний слой не знает про верхние.

```
┌──────────────────────────────────────┐
│         presentation                 │  ← UI, ViewModel
│      (Compose, StateFlow)            │     знает про domain
├──────────────────────────────────────┤
│            domain                    │  ← Модели предметной области,
│    (чистый Kotlin, без Android)      │     интерфейсы репозиториев
├──────────────────────────────────────┤
│             data                     │  ← Реализация:
│      (MediaStore-репо, Room)         │     реализует интерфейсы из domain
├──────────────────────────────────────┤
│             core                     │  ← Аудио-примитивы:
│  (AudioTrack, AudioPlayer, Scanner)  │     нужны сразу всем слоям
└──────────────────────────────────────┘
```

### Правила зависимостей

- `presentation` → `domain` ✅
- `data` → `domain` ✅
- `domain` → `core` ✅ (и отдаёт его наружу через `api`)
- `core` → ничего ❌ (кроме корутин и платформенных API)
- `domain` → `data` / `presentation` ❌
- `data` ↔ `presentation` ❌

**Почему?** Когда `domain` ни от кого не зависит, его можно:
- запустить на JVM, iOS, вебе без изменений;
- тестировать без эмулятора;
- безопасно менять `data` без страха сломать UI.

---

## Модули в нашем проекте

Открой `settings.gradle.kts` — увидишь:

```kotlin
include(":core")
include(":shared")
include(":composeApp")
include(":shared:data")
include(":shared:domain")
include(":shared:presentation")
```

Это отдельные Gradle-модули. У каждого свой `build.gradle.kts`, свои зависимости, свой `src/`.

#### Что значит `:` и как Gradle превращает его в директорию

Gradle-модуль (он же «subproject») — это **независимая единица сборки**. У него собственный classpath, собственные зависимости, собственный compilation output. Модули могут зависеть друг от друга, но компилируются отдельно.

`:` в `include(":composeApp")` — это **разделитель в Gradle-пути**, аналог `/` в файловой системе. Корень — это сам проект (где лежит `settings.gradle.kts`); каждый `:` спускается на уровень ниже:

| Gradle-путь | Директория на диске |
|---|---|
| `:core` | `mp3Player/core/` |
| `:composeApp` | `mp3Player/composeApp/` |
| `:shared` | `mp3Player/shared/` |
| `:shared:data` | `mp3Player/shared/data/` |
| `:shared:domain` | `mp3Player/shared/domain/` |
| `:shared:presentation` | `mp3Player/shared/presentation/` |

То есть `:shared:data` — это «проект `shared`, в нём подпроект `data`». Когда Gradle видит `include(":shared:data")`, он ищет директорию `shared/data/` относительно корня и читает оттуда `build.gradle.kts`.

Если бы хотелось положить модуль в нестандартное место — можно явно:

```kotlin
include(":shared:data")
project(":shared:data").projectDir = file("custom/path/data")
```

Но обычно так не делают — стандартное соответствие «:` = `/`» проще читать.

#### Что физически означает «модуль зависит от модуля»

Когда в `shared/data/build.gradle.kts` написано:

```kotlin
implementation(project(":shared:domain"))
```

это говорит Gradle: «при компиляции модуля `data` положи в classpath артефакт, собранный модулем `domain`». Это **compile-time-зависимость** — она проверяется в момент сборки, не в рантайме. Без неё классы из `domain` (например, `Album`) для компилятора `data` просто не существуют — IDE покажет «Unresolved reference: Album».

`implementation` vs `api` — это уровень видимости транзитивных зависимостей:
- `implementation(project(":shared:domain"))` — `data` видит классы `domain`, но **те, кто зависит от `data`**, не видят `domain` через эту цепочку.
- `api(project(":shared:domain"))` — все, кто зависит от `data`, автоматически получают доступ к `domain`.

У нас `api` использован ровно в одном месте — в `shared/domain/build.gradle.kts`:

```kotlin
// shared/domain/build.gradle.kts
commonMain.dependencies {
    api(projects.core)      // ← именно api, не implementation
}
```

Почему так: `TracksRepository.observeTracks()` возвращает `Flow<List<AudioTrack>>`, а `AudioTrack` живёт в `:core`. Если бы стояло `implementation`, то `presentation`, подключивший только `:shared:domain`, увидел бы функцию, чей возвращаемый тип ему неизвестен — ошибка компиляции. **Правило: тип, который торчит в публичном API модуля, должен приходить через `api`.**

`projects.core` (вместо `project(":core")`) — это **type-safe project accessor**, включается строкой `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` в `settings.gradle.kts`. Даёт автокомплит и ловит опечатки в имени модуля на этапе компиляции скрипта, а не при сборке.

### `:core` — аудио-примитивы

Живёт в `core/src/*/kotlin/org/example/mp3player/core/audio/`.

Что кладём:

- **`audio/player/`:** `AudioTrack`, `PlaybackState`, `RepeatMode`, `expect class AudioPlayer` + Android-реализация на Media3 + iOS-заглушка.
- **`audio/scanner/`:** `expect class MusicScanner` + Android-реализация на MediaStore + iOS-заглушка.

```kotlin
// core/src/commonMain/kotlin/org/example/mp3player/core/audio/player/AudioTrack.kt
package org.example.mp3player.core.audio.player

data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String,
    val path: String,
    val duration: Long,
    val coverUri: String? = null,
)
```

**Почему `AudioTrack` не в `domain`.** Модель трека нужна одновременно сканеру (он её создаёт), плееру (он её проигрывает), репозиториям и всем ViewModel. При этом `domain` уже зависит от `:core` — потому что `TracksRepository` возвращает треки, а `AudioPlayer` их принимает. Если положить `AudioTrack` в `domain`, то `:core` должен будет зависеть от `domain` — получим цикл `core → domain → core`, который Gradle просто не соберёт.

Отдельный нижний модуль эту петлю разрывает: `:core` не знает ни про кого, все знают про `:core`.

Название `AudioTrack`, а не `Track`, — сознательно: «Track» слишком общее слово, оно конфликтует и с `androidx.media3` и с «дорожкой в альбоме». Имя типа должно читаться однозначно в любом файле проекта.

### `:shared:domain` — предметная область

Живёт в `shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/`.

Что кладём:

- **`model/`:** `Album`, `UserAlbum` — модели, которых нет в `:core`, потому что плееру и сканеру они не нужны.
- **`repository/`:** `TracksRepository`, `AlbumsRepository`, `UserAlbumsRepository` — интерфейсы.
- **Use cases** (по желанию — можно и в ViewModel): `ScanMusicUseCase`, `CreateUserAlbumUseCase`.

Что **не кладём:** ничего из `android.*`, `androidx.*`, `room`, `media3`, `compose`.

Пример:

```kotlin
// shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/repository/TracksRepository.kt
package org.example.mp3player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.example.mp3player.core.audio.player.AudioTrack

interface TracksRepository {
    /** Возвращает поток треков. Переэмитит новый список при изменении медиатеки. */
    fun observeTracks(): Flow<List<AudioTrack>>

    /** Принудительно пересканировать устройство. */
    suspend fun refresh()
}
```

### `:shared:data` — реализация

Живёт в `shared/data/src/`. Исходники разбиты по платформам:

```
shared/data/src/
├── commonMain/kotlin/       # expect-заголовки, кросс-платформенный код
├── androidMain/kotlin/      # actual-реализации для Android
└── iosMain/kotlin/          # actual-заглушки для iOS
```

Что кладём:

- **`repository/`:** реализации `TracksRepository`, `AlbumsRepository`, `UserAlbumsRepository`.
- **`database/`:** `AppDatabase` (Room), `dao/`, `entities/`.
- **`di/`:** Koin-модули data-слоя (`dataModule`, `androidDataModule`, `iosDataModule`).

### `:shared:presentation` — UI и ViewModel

Живёт в `shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/`.

Что кладём:

- **Composable-экраны:** `TracksScreen`, `AlbumsScreen`, `PlayerScreen`, `AlbumDetailsScreen`, `UserAlbumsScreen`.
- **ViewModel:** `TracksViewModel`, `PlayerViewModel`, ...
- **UiState / UiEvent** — `sealed interface` или `data class`.
- **Navigation**-граф и переиспользуемые компоненты в `common/`.

Что **не кладём:** прямые вызовы MediaStore, работу с файлами, SQL — всё идёт через интерфейсы из `domain`.

### `:shared` — агрегатор

Тонкая прослойка между `composeApp` и остальными модулями: `RootScreen` (корневой Composable) и `getSharedModule()`, который собирает Koin-модули всех слоёв в один список. Нужен, чтобы `composeApp` подключал **один** модуль вместо четырёх и не знал про внутреннее устройство `shared/*`.

### `:composeApp` — точка входа

Android-специфичный модуль. Только `MainActivity`, `Application` с `startKoin`, `AndroidManifest.xml`.
Почти не содержит бизнес-логики — вся логика в `:core` и `shared:*`.

---

## Поток данных

Пример: пользователь жмёт кнопку "обновить список треков". Как это проходит через слои?

```
[TracksScreen]                         Compose, presentation
     │ onClick → viewModel.onEvent(Refresh)
     ▼
[TracksViewModel]                      presentation
     │ viewModelScope.launch { tracksRepo.refresh() }
     ▼
[TracksRepositoryImpl]                 data/repository
     │ musicScanner.scanTracks()
     ▼
[MusicScanner (actual, Android)]       core/androidMain
     │ MediaStore.query(...)
     ▼
[Android MediaStore]                    платформа
```

Ответ возвращается наверх через `Flow`:

```
[Android MediaStore]
     │
     ▼
[MusicScanner] → List<AudioTrack>
     │
     ▼
[TracksRepositoryImpl] → emit в Flow<List<AudioTrack>>
     │
     ▼
[TracksViewModel] → StateFlow<TracksUiState>
     │
     ▼
[TracksScreen] → перерисовка LazyColumn
```

**Почему именно так?** Потому что каждый слой знает только свой соседний и свои интерфейсы. ViewModel не в курсе,
что за MediaStore там внутри — ему нужен `Flow<List<AudioTrack>>`. Если завтра треки придут из сети, ViewModel не изменится.

#### Как один `Flow` физически проходит через все слои

Важный момент, который часто непонятен новичку: **на этой диаграмме нет четырёх отдельных «копий»** списка треков, по одной на каждый слой. Есть **один холодный поток данных**, к которому каждый слой добавляет свой оператор.

Псевдокод сверху вниз:

```kotlin - иллюстрация (упрощённо; настоящий код — в главах 02 и 06)
// data: репозиторий держит источник
class TracksRepositoryImpl {
    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    fun observeTracks(): Flow<List<AudioTrack>> = _tracks.asStateFlow()
}

// presentation: ViewModel добавляет операторы
class TracksViewModel(repo: TracksRepository) : ViewModel() {
    val state: StateFlow<TracksUiState> = repo.observeTracks()
        .map { tracks -> TracksUiState.Content(tracks = tracks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TracksUiState.Loading)
}

// presentation: UI подписывается
@Composable
fun TracksScreen(vm: TracksViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()  // ← подписка
    // ...
}
```

Что происходит, когда `_tracks.value = newList` в репозитории:

1. `MutableStateFlow` уведомляет всех подписчиков. Подписчик у нас один — это `.map { ... }` от ViewModel.
2. `Flow.map` получает новый `List<AudioTrack>`, прогоняет через лямбду, эмитит `TracksUiState`.
3. `stateIn` принимает эмит, обновляет своё «текущее значение».
4. `collectAsStateWithLifecycle` в Composable получает новое значение, обновляет `state`.
5. Compose видит, что `state` поменялся → перерисовка `LazyColumn`.

То есть **один и тот же бит «треки изменились»** прошёл через 4 слоя без копирования и без явных вызовов «обнови UI». Это ключевое преимущество реактивного подхода: пишешь декларативно «откуда брать → как трансформировать → куда показывать», а кто кого дёргает — рантайм решает сам.

`emit` в этой схеме — это **передача значения вниз по цепочке `Flow.map`/`Flow.combine`/...**, а `return` — это синхронный выход из обычной функции. Принципиально разные механизмы, хотя оба «возвращают значение».

Подробно про cold/hot Flow и `stateIn` — в файле [`06-VIEWMODELS_AND_STATE.md`](./06-VIEWMODELS_AND_STATE.md). Здесь главное — увидеть, что архитектура и Flow — одна и та же идея, рассказанная двумя разными словарями.

---

## `expect` / `actual` — как KMP разделяет платформы

### Проблема

`MediaStore` — это Android-API. Его нельзя вызвать из `commonMain` — там нет `android.*`. Но и писать логику сканирования дважды не хочется.

### Решение — `expect` / `actual`

В `commonMain` пишешь **заголовок** — "где-то на каждой платформе есть такой класс":

```kotlin
// core/src/commonMain/kotlin/org/example/mp3player/core/audio/scanner/MusicScanner.kt
package org.example.mp3player.core.audio.scanner

import org.example.mp3player.core.audio.player.AudioTrack

expect class MusicScanner {
    suspend fun scanTracks(): List<AudioTrack>
}
```

В `androidMain` — **реальная реализация**:

```kotlin
// core/src/androidMain/kotlin/org/example/mp3player/core/audio/scanner/MusicScanner.android.kt
package org.example.mp3player.core.audio.scanner

import android.content.Context
import org.example.mp3player.core.audio.player.AudioTrack

actual class MusicScanner(private val context: Context) {
    actual suspend fun scanTracks(): List<AudioTrack> {
        // ... MediaStore query
    }
}
```

В `iosMain` — **заглушка**, чтобы проект собирался:

```kotlin
// core/src/iosMain/kotlin/org/example/mp3player/core/audio/scanner/MusicScanner.ios.kt
package org.example.mp3player.core.audio.scanner

import org.example.mp3player.core.audio.player.AudioTrack

actual class MusicScanner {
    actual suspend fun scanTracks(): List<AudioTrack> {
        TODO("iOS implementation: использовать MPMediaQuery")
    }
}
```

Компилятор Kotlin при сборке под Android берёт `.android.kt`, при сборке под iOS — `.ios.kt`.

### Почему делаем заглушки, а не просто `expect` без `iosMain`?

Если в `commonMain` есть `expect`, то **на каждой платформе** из `kotlin { sourceSets { ... } }` должен быть `actual`. 
Иначе модуль не собирается. Заглушка с `TODO` — это формально валидная реализация: компилируется, 
но при вызове кидает `NotImplementedError`. Это нам и нужно — пока iOS не делаем, но структура готова.

### Почему `expect class`, а не просто `interface MusicScanner`?

Это закономерный вопрос: «у меня уже есть один способ скрыть реализацию — интерфейс. Зачем второй механизм?»

Разница тонкая, но важная.

**`interface` — это runtime-полиморфизм.** В скомпилированном коде есть таблица виртуальных методов (vtable), и при вызове `scanner.scanTracks()` JVM/нативная среда смотрит в эту таблицу и идёт в нужную реализацию. Это работает на одном таргете, где обе реализации скомпилированы и могут сосуществовать (например, реальная и фейковая для тестов).

**`expect/actual` — это compile-time-полиморфизм.** Нет vtable. Нет «выбора в рантайме». При компиляции под Android в classpath попадает только `MusicScanner.android.kt`, при компиляции под iOS — только `MusicScanner.ios.kt`. Это **тот же самый класс с одним и тем же именем**, просто его «тело» разное в каждой сборке.

Зачем именно так:

1. **Разные конструкторы.** Android-реализация требует `Context`. iOS-реализация — нет. Для интерфейса пришлось бы либо тащить `Context` в общий API (нарушение KMP), либо делать `Context?` (плохой дизайн), либо использовать factory-функции с разными сигнатурами в каждой платформе. С `expect class` — `actual class MusicScanner(private val context: Context)` на Android, `actual class MusicScanner` на iOS, и ты так и пишешь в DI: `single { MusicScanner(androidContext()) }` на Android, `single { MusicScanner() }` на iOS.

2. **Использование платформенных типов.** `actual` метод может возвращать `android.net.Uri` или `NSData` — типы, которые **в принципе нельзя описать в `commonMain`**. Интерфейс такого не позволит.

3. **Производительность.** Нет vtable lookup, прямой статический вызов. Для горячих путей (плеер, сканер) — заметно.

4. **Меньше кода.** Не нужны два уровня: интерфейс + реализация на каждой платформе. Один `expect class` + один `actual class` на платформу.

Когда `interface` всё-таки лучше:
- если на одной и той же платформе нужны несколько реализаций (тестовая, продакшен, отладочная) — это естественно через DI и interface;
- если поведение полностью описывается в общих типах — интерфейс проще и переноснее.

В нашем проекте оба механизма сосуществуют:
- `TracksRepository` — это **interface** в `domain`, потому что в `commonMain` мы хотим возможность подменять реализацию (тесты + текущий MediaStore-вариант + потенциальный сетевой вариант).
- `MusicScanner` и `AudioPlayer` — это **`expect class`** в `:core`, потому что их конструкторы и внутренности привязаны к платформенному API (`Context`, MediaStore, Media3).

---

## Как модули настраиваются в Gradle

Коротко, чтобы ты понимал что видишь, открывая `build.gradle.kts` в `shared/data/`:

```kotlin
// core/build.gradle.kts
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    androidLibrary {
        namespace = "dev.rostisla.mp3player.core"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    )

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        androidMain {
            dependencies {
                // MediaStore идёт с SDK, а вот плеер — отдельные артефакты
                implementation(libs.androidx.media3.exoplayer)
                implementation(libs.androidx.media3.session)
            }
        }
        iosMain {
            dependencies { }
        }
    }
}
```

**Ключевое:**
- `libs.*` — это версии из `gradle/libs.versions.toml` (Version Catalog).
- `androidLibrary { }` (плагин `com.android.kotlin.multiplatform.library`) — новый способ описать Android-таргет KMP-модуля. Раньше писали `androidTarget()` + отдельный блок `android { }` от `com.android.library`; новый плагин объединяет их в один блок, поэтому в проекте нет `alias(libs.plugins.androidLibrary)`.
- `listOf(iosArm64(), iosSimulatorArm64())` — только два iOS-таргета: реальное устройство и симулятор на Apple Silicon. `iosX64` (симулятор на Intel-маках) не подключаем — он нам не нужен и удваивает время сборки.

Зависимости между модулями:

```kotlin - иллюстрация (по одной строке из каждого build.gradle.kts)
// shared/domain/build.gradle.kts
api(projects.core)                        // api: AudioTrack торчит в сигнатурах репозиториев

// shared/data/build.gradle.kts
implementation(projects.shared.domain)    // implementation: наружу ничего не отдаём

// shared/presentation/build.gradle.kts
implementation(projects.shared.domain)    // :core приедет транзитивно через api выше
```

Обрати внимание: `presentation` **не подключает `:core` напрямую**, но пользуется `AudioTrack` и `AudioPlayer`. Работает это именно за счёт `api(projects.core)` в `domain`.

#### Что такое `libs.versions.toml` и `alias(...)`

В корне проекта есть файл `gradle/libs.versions.toml` — это **Gradle Version Catalog**, централизованный список зависимостей и плагинов. Структура:

```toml
[versions]
kotlin = "2.3.20"
coroutines = "1.10.2"
room = "2.8.4"

[libraries]
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidLibrary = { id = "com.android.library", version = "8.7.3" }
```

Gradle автоматически генерирует Kotlin-объект `libs`, в котором имена из TOML превращаются в свойства:

| В TOML | В `build.gradle.kts` |
|---|---|
| `kotlinx-coroutines-core` | `libs.kotlinx.coroutines.core` |
| `androidx-room-runtime` | `libs.androidx.room.runtime` |
| `kotlinMultiplatform` (plugin) | `libs.plugins.kotlinMultiplatform` |

Дефис в TOML заменяется на точку в Kotlin. Регистр сохраняется (camelCase остаётся camelCase).

`alias(libs.plugins.kotlinMultiplatform)` — короткая запись применения плагина с версией из каталога. Эквивалентно:

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.3.20"
}
```

Зачем каталог нужен:
- **Одно место для версий.** Поднял Kotlin до 2.3.21 — поправил один `[versions]`, все модули обновились.
- **Одно место для имён артефактов.** Не надо в каждом `build.gradle.kts` помнить полное `"androidx.room:room-runtime:2.8.4"`.
- **Type safety + автокомплит.** IDE подсказывает доступные `libs.*`.

#### Что такое `sourceSets { commonMain.dependencies { ... } }`

`sourceSets` — это **набор «директорий компиляции»** для KMP-модуля. Каждый source set:
- видит свои `src/<setName>/kotlin/`, `src/<setName>/resources/` и т.д.;
- имеет свой набор зависимостей;
- может зависеть от других source set'ов.

Для нашего KMP-модуля по умолчанию есть:

```
core/src/
├── commonMain/      ← общий код (виден всем target'ам)
├── androidMain/     ← виден только при сборке под Android
└── iosMain/         ← виден только при сборке под iOS
```

Когда Gradle компилирует под Android-target, в classpath попадает `commonMain` + `androidMain` (плюс зависимости). Когда под iOS — `commonMain` + `iosMain`. `expect`-декларация из `commonMain` ищет свой `actual` среди файлов того source set'а, который входит в текущую сборку.

Запись:
```kotlin
sourceSets {
    commonMain.dependencies {
        implementation(libs.kotlinx.coroutines.core)
    }
    androidMain.dependencies {
        implementation(libs.androidx.media3.exoplayer)
    }
}
```

читается как «все source set'ы — добавь корутины; только в Android-сборку — Media3». На iOS-сборке Media3 не подключается, и компилятор iOS даже не знает про эти классы.

#### Почему `kotlinx.coroutines` в `domain` — не нарушение «чистого Kotlin»

Правило мы сформулировали как «`domain` не зависит ни от чего, кроме чистого Kotlin + корутин». Зачем оговорка про корутины?

Формально `kotlinx.coroutines` — это внешняя библиотека, не часть стандартного Kotlin. Но:

1. **Она сама KMP.** У неё есть полные artefacts для JVM, Android, iOS, JS, Native. Подключение в `commonMain` не привязывает `domain` к Android.
2. **`Flow` — стандарт де-факто** для асинхронных API в Kotlin-экосистеме. Без него интерфейс репозитория пришлось бы сделать на колбэках или Reactive Streams, что менее удобно и более тяжеловесно.
3. **Отказ от корутин в `domain`** означает, что `interface TracksRepository { fun observeTracks(): ??? }` — надо либо давать `List<AudioTrack>` (тогда не реактивно), либо `Observable` из RxJava (Android-only), либо своё API. Все варианты хуже.

Так что `kotlinx.coroutines.flow.Flow` в `domain` — допустимое исключение. А вот `kotlinx.coroutines.android` (с `Dispatchers.Main` Android-реализацией) или `kotlinx.coroutines.test` — уже не пускать в `domain`, они платформо-зависимые.

---

## Разбор: почему `domain` должен быть чистым

Представь, что в `domain/repository/TracksRepository.kt` ты написал:

```kotlin - пример (как НЕ надо, не писать)
import android.content.Context   // 🚫

interface TracksRepository {
    fun observeTracks(context: Context): Flow<List<AudioTrack>>
}
```

Что не так:
1. `commonMain` не имеет доступа к `android.*` — **не скомпилируется**.
2. Даже если бы мог — интерфейс теперь зависит от Android. Зачем ему?
3. Тесты `domain` больше не запустить на JVM — нужен Android.

Правильно — `Context` не должен утечь в `domain`. Он нужен только в реализации `MusicScanner` (Android), и туда его передаёт DI (Koin).

---

## Подводные камни

### 1. Модели в неправильном слое
Модель, которую использует и нижний, и верхний слой, нельзя класть в середину — получишь круговую зависимость. `AudioTrack` нужен и сканеру, и плееру, и ViewModel, поэтому он в самом низу, в `:core`. `Album` нужен только выше сканера — он в `domain/model/`. Если сомневаешься: «кто самый нижний из тех, кому нужен этот тип?» — туда и клади.

### 2. Забытый `actual`
Если добавил `expect class X` в `commonMain` и не добавил `actual` в `iosMain` — Android-сборка пройдёт, а iOS сломается. Всегда добавляй заглушку сразу.

### 3. Утечка Android-типов в `domain`
Никогда не импортируй `android.*`, `androidx.*`, `room.*` в `shared:domain`. Даже если "очень нужно" — это сигнал, 
что абстракция протекает и надо её переделать.

### 4. Кросс-зависимости модулей
`presentation` не должен зависеть от `data`, а `data` — от `presentation`. Если кажется, что нужно — ты что-то смешал. Перенеси общую часть в `domain`.

### 5. `projects.shared.domain` забыт
Если в `build.gradle.kts` модуля `data` забыл подключить `domain` — классы из `domain` не видны, IDE ругается «Unresolved reference». Первое, что проверь.

### 6. `implementation` вместо `api` для типа из публичной сигнатуры
`shared/domain` подключает `:core` через `api(projects.core)`. Поменяй на `implementation` — и `presentation` перестанет видеть `AudioTrack`, хотя `TracksRepository.observeTracks()` его возвращает. Ошибка выглядит загадочно: «Unresolved reference: AudioTrack» в файле, где `AudioTrack` даже не импортирован явно.

---

## Try yourself

1. **Открой `settings.gradle.kts`** в корне проекта. Найди все `include(...)` и сопоставь с директориями. Убедись, что понимаешь, что такое модуль.

2. **Открой `shared/data/build.gradle.kts`**. Найди блок `sourceSets { commonMain { dependencies { ... } } }`. Там есть `projects.shared.domain`? А `:core` — есть напрямую? (Не должно быть: он приезжает транзитивно.)

3. **Нарисуй от руки**: куда положить классы ниже?
   - `class EqualizerController` (работа с `AudioEffect` API) → ?
   - `data class EqualizerPreset(val name: String, val bands: List<Float>)` → ?
   - `@Composable fun EqualizerScreen(...)` → ?
   - `interface EqualizerRepository` → ?
   - `class EqualizerViewModel` → ?

   Ответы: контроллер завязан на платформенный аудио-API и нужен снизу — `core/androidMain` (плюс `expect` в `core/commonMain`); пресет — это модель предметной области, её не трогают ни сканер, ни плеер — `domain/model`; экран и ViewModel — `presentation/commonMain`; интерфейс репозитория — `domain/repository`.

4. **Проверь себя на цикле**: попробуй мысленно положить `EqualizerPreset` в `:core`, а `EqualizerController` — в `domain`. Где сломается и почему?

5. **Перечитай `GUIDE.md` в корне проекта**. Теперь он читается иначе — ты видишь, что именно имелось в виду под «слоями».

---

## Дальше

→ [`02-PERMISSIONS_AND_SCAN.md`](./02-PERMISSIONS_AND_SCAN.md)

## Ссылки

- [Guide to app architecture — Android Developers](https://developer.android.com/topic/architecture)
- [Kotlin Multiplatform — expected and actual declarations](https://kotlinlang.org/docs/multiplatform-expect-actual.html)
- [Clean Architecture by Uncle Bob (статья)](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
