# 05. Dependency Injection — Koin

## Зачем

К этому моменту у нас уже есть: `MusicScanner`, `TracksRepositoryImpl`, `AlbumsRepositoryImpl`, `UserAlbumsRepositoryImpl`, `AudioPlayer`, `AppDatabase`, а дальше будут `TracksViewModel`, `AlbumsViewModel`, `PlayerViewModel`. Кто-то должен всё это создавать и **отдавать** в места, где это нужно.

Плохой вариант:
```kotlin
class TracksViewModel : ViewModel() {
    private val repository = TracksRepositoryImpl(MusicScanner(/* где взять Context? */))
    // ...
}
```
Проблемы:
- Нельзя тестировать (никак не подменить реальный `MusicScanner`).
- ViewModel должен знать про конструктор `TracksRepositoryImpl` → зависит от `data`-слоя.
- Циклы создания: кто создаст `Context` для сканера в `commonMain`?

Хороший вариант — DI-контейнер создаёт всё сам, ViewModel только получает готовое.

**Koin** выбран потому, что:
- идиоматичен для Kotlin (DSL из блоков `module { single { ... } }`),
- работает в KMP,
- нет генерации кода (в отличие от Dagger/Hilt) → проще для новичка,
- есть `koinViewModel()` для Compose.

---

## Что реализуем

1. Подключим Koin.
2. Три модуля: `dataModule`, `domainModule` (почти пустой, но пригодится для UseCase), `presentationModule`.
3. Platform-specific модуль `androidDataModule` с `Context`-зависимыми вещами.
4. Точка входа `startKoin` в `MainActivity`.
5. Использование `koinViewModel()` в Compose.

Новые файлы:

```
shared/data/src/
├── commonMain/kotlin/org/example/mp3player/data/di/
│   └── DataModule.kt                     (новый, common-часть)
├── androidMain/kotlin/org/example/mp3player/data/di/
│   └── AndroidDataModule.kt              (новый, Android-часть)
└── iosMain/kotlin/org/example/mp3player/data/di/
    └── IosDataModule.kt                  (новый, заглушка)

shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/di/
└── PresentationModule.kt                 (новый)

composeApp/src/androidMain/kotlin/org/example/mp3player/
├── App.kt                                (новый, Application)
└── MainActivity.kt                       (обновить: startKoin)
```

---

## Реализация

### Шаг 1 — Зависимости

Koin делится на артефакты: `koin-core` (ядро, KMP), `koin-android` (расширения с `Context`/`Application`), `koin-compose` (получение зависимостей в `@Composable`), `koin-compose-viewmodel` (factory для ViewModel в Compose).

Добавляем версии и артефакты:

```toml
# gradle/libs.versions.toml
[versions]
koin = "4.2.0-RC1"

[libraries]
koin-core = { group = "io.insert-koin", name = "koin-core", version.ref = "koin" }
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-compose-viewmodel = { group = "io.insert-koin", name = "koin-compose-viewmodel", version.ref = "koin" }
koin-compose = { group = "io.insert-koin", name = "koin-compose", version.ref = "koin" }
```

Подключаем по модулям — в `data` (ядро + Android-расширения):

```kotlin
// shared/data/build.gradle.kts
commonMain.dependencies {
    implementation(libs.koin.core)
}
androidMain.dependencies {
    implementation(libs.koin.android)
}
```

В `presentation` (ядро + Compose-обёртки):

```kotlin
// shared/presentation/build.gradle.kts
commonMain.dependencies {
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
}
```

В `composeApp` (для `startKoin` в Application):

```kotlin
// composeApp/build.gradle.kts
androidMain.dependencies {
    implementation(libs.koin.android)
}
```

### Шаг 2 — Data-модуль (common-часть)

Модули Koin — это «инструкции по сборке зависимостей». Создаём общую часть data-модуля: репозитории `TracksRepositoryImpl` и `AlbumsRepositoryImpl`, у которых нет platform-specific зависимостей.

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/di/DataModule.kt
package org.example.mp3player.data.di

import org.example.mp3player.data.AlbumsRepositoryImpl
import org.example.mp3player.data.TracksRepositoryImpl
import org.example.mp3player.domain.AlbumsRepository
import org.example.mp3player.domain.TracksRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Общая часть data-модуля.
 * Platform-specific зависимости (Context, Room, ExoPlayer) — в androidDataModule/iosDataModule.
 */
val dataModule = module {
    singleOf(::TracksRepositoryImpl) { bind<TracksRepository>() }
    singleOf(::AlbumsRepositoryImpl) { bind<AlbumsRepository>() }
}
```

**`module { ... }` — что это.** Функция из `org.koin.dsl`:

```kotlin
fun module(createdAtStart: Boolean = false, moduleDeclaration: ModuleDeclaration): Module
```

`ModuleDeclaration` — это `Module.() -> Unit`, **лямбда, внутри которой `this` это `Module`**. Поэтому когда пишешь `singleOf(...)` — это эквивалентно `this.singleOf(...)`, где `this` это `Module`. Внутри блока доступны все методы: `single`, `factory`, `viewModel`, `singleOf`, `viewModelOf`. Это и называется **DSL** — domain-specific language через лямбды с receiver.

`module` возвращает объект, в котором собраны все «определения» (`Definition` — каждый описывает «как создать X»). При `startKoin { modules(dataModule, ...) }` определения собираются в один граф.

**`single` vs `factory` vs `viewModel`** — три типа жизненного цикла:

| Scope | Поведение | Когда использовать |
|-------|-----------|--------------------|
| `single` | Создаётся один раз, кешируется навсегда | `AppDatabase`, `AudioPlayer`, репозитории |
| `factory` | Новый экземпляр на каждый `get()` | Stateful-объекты короткого времени жизни |
| `viewModel` | Один на жизненный цикл Compose-дестинации/Activity | ViewModel-ы |

Репозитории — `single`, потому что у них есть внутреннее состояние (`_tracks: MutableStateFlow`), которое должно быть **общим** для всего приложения. Если бы каждый ViewModel получал свой экземпляр `TracksRepositoryImpl`, они бы не знали друг про друга.

**`singleOf(::X) { bind<Y>() }`** — главная идиома Koin для DI через конструктор:

`::TracksRepositoryImpl` — это **constructor-reference**. В Kotlin `::ClassName` означает «дай мне ссылку на конструктор класса». Тип такого выражения — соответствующая `KFunctionN`, где N — количество параметров конструктора.

`singleOf` принимает constructor-reference и **сам вызывает `get()` для каждого параметра**:

```kotlin
// Вручную (полная форма):
single<TracksRepository> { TracksRepositoryImpl(get()) }

// Через singleOf — Koin сам разрешает параметры:
singleOf(::TracksRepositoryImpl) { bind<TracksRepository>() }
```

«Сам разрешает» — это не рефлексия в рантайме (это было бы медленно). Под капотом у `singleOf` несколько перегрузок для конструкторов с 1, 2, 3, ... параметров. Компилятор Kotlin выбирает нужную перегрузку по типу `KFunctionN`. На рантайме просто вызов через function reference — почти бесплатно.

Преимущество: если конструктор изменится (добавится новая зависимость), `singleOf` **автоматически** подставит её через `get()`. Не надо обновлять модуль вручную.

`{ bind<TracksRepository>() }` — блок «опций»: «зарегистрируй ЭТОТ же объект ещё под этим типом». Без него `get<TracksRepository>()` не нашёл бы реализацию — Koin искал бы зарегистрированный тип, а зарегистрирован `TracksRepositoryImpl`, не интерфейс.

### Шаг 3 — Android-специфичная часть

В Android-таргете живут зависимости с `Context`: Room, `MusicScanner`, `AudioPlayer`.

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/di/AndroidDataModule.kt
package org.example.mp3player.data.di

import org.example.mp3player.data.MusicScanner
import org.example.mp3player.data.UserAlbumsRepositoryImpl
import org.example.mp3player.data.db.AppDatabase
import org.example.mp3player.data.db.dao.UserAlbumsDao
import org.example.mp3player.data.player.AudioPlayer
import org.example.mp3player.domain.UserAlbumsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val androidDataModule = module {
    single { AppDatabase.build(androidContext()) }
    single<UserAlbumsDao> { get<AppDatabase>().userAlbumsDao() }

    single { MusicScanner(androidContext()) }
    single { AudioPlayer(androidContext()) }

    singleOf(::UserAlbumsRepositoryImpl) { bind<UserAlbumsRepository>() }
}
```

Тут несколько важных деталей.

**`androidContext()` — где он живёт.** Технически Koin (`koin-core`) не знает, что такое `Context` — это Android-понятие. Артефакт `koin-android` добавляет:

1. **DSL-функцию `androidContext(app: Application)`** в блок `startKoin { ... }`. Она регистрирует переданное `app` (т.е. `Context`) как синглтон в Koin.
2. **DSL-функцию `androidContext()` в `Module`-scope**. Она достаёт зарегистрированный `Context` через `get<Context>()`.

Без шага 1 (в `App.onCreate` — см. Шаг 6) при попытке достать `androidContext()` в модуле будет `NoDefinitionFoundException: No definition found for class:'Context'`.

**`single { ... }` — это lazy.** Деталь, которую часто пропускают: при `startKoin` Koin **не создаёт** ни одного объекта. Лямбда внутри `single { ... }` выполнится **только при первом вызове `get<Type>()`** (или транзитивно — когда другая зависимость попросит этот тип).

Это значит:
- `startKoin` дешёвый — он только регистрирует определения.
- `single { AppDatabase.build(androidContext()) }` — БД не открывается при старте, она откроется при первом `dao.observeAll()`.
- Можешь регистрировать «дорогие» зависимости в большом модуле и не платить за них, пока не нужны.

После первого создания результат **кешируется**: каждый последующий `get()` вернёт тот же объект (поэтому `AudioPlayer` действительно один на приложение).

**`get()` внутри блока `single { ... }`** — «возьми зависимость этого типа». Тип вычисляется через **reified type parameter**: компилятор по контексту понимает, какой `Type` нужен.

```kotlin
single { AudioPlayer(get()) }
```

`AudioPlayer` принимает `Context`, поэтому `get()` тут разрешится как `get<Context>()`. Компилятор видит сигнатуру конструктора и подставляет правильный тип.

Если зависимость не зарегистрирована — `NoDefinitionFoundException` при первом запросе. Чтобы поймать это раньше, в unit-тесте можно прогнать `checkKoinModules()` — он проверит весь граф.

**`single<UserAlbumsDao> { get<AppDatabase>().userAlbumsDao() }`** — пример «достать зависимость через другую». Сначала Koin создаст `AppDatabase` (если ещё не создан), потом вызовет на нём `userAlbumsDao()` и закеширует результат. Так Room-DAO становится DI-зависимостью, не зная про сам Room.

### Шаг 4 — iOS-заглушка

Симметрично Android-модулю — для iOS-таргета.

```kotlin
// shared/data/src/iosMain/kotlin/org/example/mp3player/data/di/IosDataModule.kt
package org.example.mp3player.data.di

import org.example.mp3player.data.MusicScanner
import org.example.mp3player.data.player.AudioPlayer
import org.koin.dsl.module

val iosDataModule = module {
    single { MusicScanner() }
    single { AudioPlayer() }
    // UserAlbumsRepository — отдельная задача, пока iOS не используем.
}
```

Конструкторы без параметров — у iOS-`actual` нет `Context`. `UserAlbumsRepositoryImpl` пропущен: на iOS его реализация будет через что-то другое (Core Data / SQLDelight), мы пока этим не занимаемся.

### Шаг 5 — Presentation-модуль

Регистрируем все ViewModel'и. Это `commonMain` — KMP-модуль, никакой Android-специфики.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/di/PresentationModule.kt
package org.example.mp3player.presentation.di

import org.example.mp3player.presentation.albums.AlbumsViewModel
import org.example.mp3player.presentation.albumdetails.AlbumDetailsViewModel
import org.example.mp3player.presentation.player.PlayerViewModel
import org.example.mp3player.presentation.tracks.TracksViewModel
import org.example.mp3player.presentation.useralbums.UserAlbumsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::TracksViewModel)
    viewModelOf(::AlbumsViewModel)
    viewModelOf(::AlbumDetailsViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::UserAlbumsViewModel)
}
```

`viewModelOf(::X)` — то же что `singleOf(::X)`, но с другим scope: один экземпляр на жизненный цикл Compose-дестинации (или Activity). Когда дестинация уходит со стека — `onCleared()` ViewModel вызывается, экземпляр освобождается.

Сами ViewModel'и пока не написаны — это следующий этап (`06-VIEWMODELS_AND_STATE.md`). Регистрируем их заранее, чтобы DI-граф был готов.

### Шаг 6 — Application + startKoin

Точка входа для Koin — `Application.onCreate()`. Это место, куда Android отдаёт управление при старте процесса, до создания любой Activity.

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/App.kt
package org.example.mp3player

import android.app.Application
import org.example.mp3player.data.di.androidDataModule
import org.example.mp3player.data.di.dataModule
import org.example.mp3player.presentation.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@App)
            modules(
                dataModule,
                androidDataModule,
                presentationModule,
            )
        }
    }
}
```

Регистрируем `App` в манифесте, чтобы Android знал использовать его вместо дефолтного `Application`:

```xml
<!-- composeApp/src/androidMain/AndroidManifest.xml -->
<application
    android:name="org.example.mp3player.App"
    ...>
```

**Что физически делает `startKoin { ... }`.** Создаёт `KoinApplication` (контейнер настроек) и `Koin` (рантайм с графом зависимостей). После выполнения:
- В глобальной точке (через `GlobalContext`) лежит ссылка на текущий `Koin`.
- Все определения из переданных модулей зарегистрированы в графе.
- Никаких объектов ещё не создано (single — lazy, см. Шаг 3).

Дальше в любом месте приложения:
- `getKoin().get<Type>()` — достать зависимость напрямую.
- `class X : KoinComponent { val y: Y by inject() }` — через делегат `inject`.
- В Compose — `koinViewModel()` (Шаг 7).

`startKoin` нельзя вызывать дважды в одном процессе — упадёт с `KoinAppAlreadyStartedException`. Поэтому стандартное место — `Application.onCreate()`, который Android вызывает один раз при старте процесса.

`androidLogger(Level.INFO)` — Koin будет логировать создание зависимостей. В проде заменишь на `Level.NONE`, чтобы не засорять logcat.

### Шаг 7 — Использование в Compose

Получаем ViewModel из Koin прямо в Composable через `koinViewModel()`:

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/tracks/TracksScreen.kt
package org.example.mp3player.presentation.tracks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TracksScreen(
    onOpenPlayer: () -> Unit,
    viewModel: TracksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // ...
}
```

Koin сам создаёт `TracksViewModel`, подставит туда `TracksRepository`, `AudioPlayer` — что бы ни потребовалось в конструкторе.

**`koinViewModel()` vs `viewModel()`.** `koinViewModel()` (из `koin-compose-viewmodel`) — обёртка над `viewModel()` из Compose:

```kotlin
@Composable
inline fun <reified T : ViewModel> koinViewModel(...): T {
    return viewModel<T>(factory = KoinViewModelFactory(...))
}
```

Использует стандартный механизм Compose + Lifecycle, но передаёт ему **factory из Koin**. Factory знает, как через граф зависимостей собрать ViewModel: для каждого параметра конструктора зовёт `get()`.

«Один на дестинацию» означает: `viewModel()` использует `ViewModelStore` текущего `LocalViewModelStoreOwner`. В навигационных компонентах эта область — `NavBackStackEntry` (для `composable<...>`) или Activity. Пока область жива — ViewModel живёт. Уходит — `onCleared()`.

Если случайно использовать `viewModel()` (без `koin`) с ViewModel'ом, имеющим непустой конструктор — `RuntimeException: Cannot create an instance of class TracksViewModel`. Compose сам не знает, как собирать аргументы; нужна factory. `koinViewModel()` это и решает.

**Почему конструктор-инъекция, а не field-инъекция.** Koin поддерживает оба варианта:

```kotlin
// Конструктор-инъекция (рекомендуется):
class TracksViewModel(private val repo: TracksRepository) : ViewModel() { ... }

// Field-инъекция через KoinComponent:
class TracksViewModel : ViewModel(), KoinComponent {
    private val repo: TracksRepository by inject()
}
```

Конструктор лучше потому, что:
1. **Тестируемость.** В тесте создаёшь `TracksViewModel(FakeRepository())` — никакого Koin не нужно. С field-инъекцией пришлось бы стартовать Koin в тесте.
2. **Явность.** По конструктору видно, что нужно классу. Field-инъекция «прячет» зависимости.
3. **Cycle detection.** Если A нужен B, B нужен A — конструкторная инъекция упадёт сразу при `singleOf`. Field — runtime.

Field-инъекция через `KoinComponent` оставлена для случаев, когда конструктор недоступен (Android-системные классы вроде `BroadcastReceiver`, которые Android создаёт сам).

### Шаг 8 — Вызов из не-Composable мест

Иногда надо получить зависимость вне Composable (например, в `Application.onCreate`):

```kotlin
import org.koin.java.KoinJavaComponent.getKoin

val audioPlayer: AudioPlayer = getKoin().get()
```

Или если класс реализует `KoinComponent`:

```kotlin
class SomeHelper : KoinComponent {
    private val repo: TracksRepository by inject()
}
```

Но **в ViewModel инжекть через конструктор**, не через `inject()` — см. объяснение в Шаге 7.

> **Внимание про `runCatching` в обработке ошибок.** Когда будешь в `06-VIEWMODELS_AND_STATE.md` писать try/catch на корутинных вызовах через Koin — не используй `runCatching { ... }` без фильтра. Он ловит **все** `Throwable`, включая `CancellationException` (сигнал отмены корутины — `viewModelScope` отменяется при `onCleared`). Этот сигнал должен пробрасываться вверх. Правильный паттерн: `runCatching { ... }.onFailure { if (it is CancellationException) throw it else ... }`. Подробнее в файле 06.

---

## Подводные камни

### 1. `startKoin` дважды
Если вызвать дважды (например, при смене конфигурации) — `KoinApplication ALREADY started`. Поэтому делай в `Application.onCreate` (а не в Activity) и не вызывай `startKoin` в тестах.

### 2. Забыт `android:name=".App"` в манифесте
`Application.onCreate` не вызовется → Koin не стартует → при первом `koinViewModel()` получишь `KoinApplicationNotStarted`.

### 3. Context передан как `Activity`
В `androidContext(this@App)` — `this` это `Application`. Если случайно `androidContext(activity)` — Koin будет держать ссылку на Activity, утечка памяти.

### 4. Неправильный scope
Если зарегистрировать `single { AudioPlayer() }`, а потом сделать `factory { AudioPlayer() }` — на каждом `get()` новый плеер, это сломает всё. Плеер должен быть один.

### 5. Циклические зависимости
`A(B)`, `B(A)` → `StackOverflowError` при `get()`. Симптом: приложение падает при старте экрана. Разбирай граф руками.

### 6. Разные модули регистрируют один тип
Если `dataModule` и `androidDataModule` оба `single<TracksRepository>` — Koin возьмёт последний зарегистрированный. Опасно, сбивает с толку. Проверь, что нет пересечений.

### 7. `koinViewModel()` и `viewModel()` из `androidx.lifecycle` не взаимозаменяемы
Используй именно **koin-compose-viewmodel**. Обычный `viewModel()` не знает про Koin.

### 8. ViewModel без пустого конструктора
Обычный `viewModel()` из Compose требует либо пустой конструктор, либо Factory. `koinViewModel()` сам делает Factory через граф — но только если зависимости зарегистрированы.

---

## Try yourself

1. **Проверь старт**: добавь `Log.d("Koin", "started")` в `App.onCreate`. Запусти — должно быть в Logcat.

2. **checkModules**: напиши unit-тест, прогоняющий `checkKoinModules()`. Убедись, что все ViewModel создаются.

3. **Подмени `MusicScanner` на fake**: в тесте запусти `startKoin { modules(module { single<MusicScanner> { FakeScanner() } }) }`. Это паттерн для тестов.

4. **Добавь `PreferencesRepository`**: `interface` в `domain`, реализация на `SharedPreferences` в `data/androidMain`, регистрация в `androidDataModule`. Проверь, что `TracksViewModel` может получить его через конструктор без изменения UI-кода.

5. **Читай логи Koin**: при `androidLogger(Level.DEBUG)` увидишь каждый запрос/создание. Полезно для отладки "почему что-то не инжектится".

---

## Дальше

→ [`06-VIEWMODELS_AND_STATE.md`](./06-VIEWMODELS_AND_STATE.md)

## Ссылки

- [Koin for Kotlin Multiplatform](https://insert-koin.io/docs/reference/koin-mp/kmp/)
- [Koin ViewModel — Jetpack Compose](https://insert-koin.io/docs/reference/koin-compose/compose#viewmodel-for-composable)
- [Koin checkModules](https://insert-koin.io/docs/reference/koin-test/checkmodules)
