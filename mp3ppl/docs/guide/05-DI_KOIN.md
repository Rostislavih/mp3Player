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

`gradle/libs.versions.toml`:

```toml
[versions]
koin = "4.2.0-RC1"

[libraries]
koin-core = { group = "io.insert-koin", name = "koin-core", version.ref = "koin" }
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-compose-viewmodel = { group = "io.insert-koin", name = "koin-compose-viewmodel", version.ref = "koin" }
koin-compose = { group = "io.insert-koin", name = "koin-compose", version.ref = "koin" }
```

`shared/data/build.gradle.kts`:
```kotlin
commonMain.dependencies {
    implementation(libs.koin.core)
}
androidMain.dependencies {
    implementation(libs.koin.android)
}
```

`shared/presentation/build.gradle.kts`:
```kotlin
commonMain.dependencies {
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
}
```

`composeApp/build.gradle.kts`:
```kotlin
androidMain.dependencies {
    implementation(libs.koin.android)
}
```

### Шаг 2 — Data-модуль (common-часть)

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

### Шаг 3 — Android-специфичная часть

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

`androidContext()` — хелпер Koin-Android. Возвращает `Context`, который мы передали при `startKoin`.

### Шаг 4 — iOS-заглушка

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

### Шаг 5 — Presentation-модуль

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

ViewModels — в следующем файле, пока просто зарегистрировали.

### Шаг 6 — Application + startKoin

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

Зарегистрировать `App` в манифесте:

```xml
<application
    android:name="org.example.mp3player.App"
    ...>
```

### Шаг 7 — Использование в Compose

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

Вот и всё. Koin сам создаст `TracksViewModel`, подставит туда `TracksRepository`, `AudioPlayer`, что бы ни потребовалось в конструкторе.

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

Но **в ViewModel инжекть через конструктор**, не через `inject()`. Конструктор — чище, тестируемее.

---

## Разбор

### `module { ... }` — что это вообще

`module` — это функция из `org.koin.dsl`, принимающая лямбду с receiver `Module`:

```kotlin
fun module(createdAtStart: Boolean = false, moduleDeclaration: ModuleDeclaration): Module
```

`ModuleDeclaration` — это `Module.() -> Unit`, то есть **лямбда, внутри которой `this` это `Module`**. Поэтому когда ты пишешь:

```kotlin
val dataModule = module {
    singleOf(::TracksRepositoryImpl) { bind<TracksRepository>() }
}
```

это эквивалентно:

```kotlin
val dataModule = module {
    this.singleOf(...)   // this = Module
}
```

Внутри блока тебе доступны все методы `Module`: `single`, `factory`, `viewModel`, `singleOf`, `viewModelOf` и т.д. Это и называется **DSL** — domain-specific language через лямбды с receiver.

Возвращает `module` объект, в котором собраны все «определения» (`Definition`-объекты — каждый описывает «как создать X»). При `startKoin { modules(dataModule, ...) }` все эти определения собираются в один граф.

### `single` vs `factory` vs `viewModel`

| Scope | Поведение | Когда использовать |
|-------|-----------|--------------------|
| `single` | Создаётся один раз, кешируется навсегда | `AppDatabase`, `AudioPlayer`, репозитории |
| `factory` | Новый экземпляр на каждый `get()` | Stateful-объекты короткого времени жизни |
| `viewModel` | Один на жизненный цикл Compose-дестинации/Activity | ViewModel-ы |

`AudioPlayer` — `single`, потому что плеер один на приложение. Две разные "копии" конфликтовали бы за `MediaController`.

#### `single { ... }` — это lazy

Важная деталь, которую часто пропускают: при `startKoin` Koin **не создаёт** ни одного объекта. Лямбда внутри `single { ... }` выполнится **только при первом вызове `get<Type>()`** (или транзитивно — когда другая зависимость попросит этот тип).

Это значит:
- `startKoin` дешёвый — он только регистрирует определения, реально ничего не строит.
- Если у тебя `single { AppDatabase.build(androidContext()) }` — БД не открывается при старте приложения, она откроется при первом `dao.observeAll()`.
- Можешь регистрировать «дорогие» зависимости в большом модуле и не платить за них, пока не нужны.

После первого создания результат **кешируется**: каждый последующий `get()` вернёт тот же объект (поэтому `AudioPlayer` действительно один на приложение).

`createdAtStart = true` (опция модуля или конкретного `single`) меняет поведение на «создать сразу при `startKoin`». Используется редко — для зависимостей, которые должны зарегистрировать что-то в системе как побочный эффект (например, инициализация Crashlytics).

### `singleOf(::X) { bind<Y>() }` — где здесь магия

```kotlin
singleOf(::TracksRepositoryImpl) { bind<TracksRepository>() }
```

`::TracksRepositoryImpl` — это **constructor-reference**. В Kotlin `::ClassName` означает «дай мне ссылку на конструктор класса». Тип такого выражения — соответствующая `KFunctionN`, где N — количество параметров конструктора.

`singleOf` принимает constructor-reference и **сам вызывает `get()` для каждого параметра**:

```kotlin
// Вручную:
single<TracksRepository> { TracksRepositoryImpl(get()) }

// Через singleOf — Koin сам разрешает параметры:
singleOf(::TracksRepositoryImpl) { bind<TracksRepository>() }
```

«Сам разрешает» — это не рефлексия в рантайме (это было бы медленно). Под капотом у `singleOf` есть несколько перегрузок для конструкторов с 1, 2, 3, ... параметров. Компилятор Kotlin выбирает нужную перегрузку по типу `KFunctionN`. На рантайме просто вызов через function reference — почти бесплатно.

Преимущество: если конструктор изменится (добавится новая зависимость), `singleOf` **автоматически** подставит её через `get()`. Не надо помнить про каждый ручной `single { ... }` и обновлять вручную.

`{ bind<TracksRepository>() }` — отдельный блок «опций»: «зарегистрируй ЭТОТ же объект ещё под этим типом». Без него `get<TracksRepository>()` не нашёл бы реализацию — Koin искал бы зарегистрированный тип, а зарегистрировано `TracksRepositoryImpl`, а не интерфейс.

### `get()`

Внутри блока `single { ... }` вызов `get<Type>()` — "возьми зависимость этого типа". Тип вычисляется через **reified type parameter**: компилятор по контексту понимает, какой `Type` нужен. Например:

```kotlin
single { AudioPlayer(get()) }
```

`AudioPlayer` принимает `Context`, поэтому `get()` тут разрешится как `get<Context>()`. Компилятор видит сигнатуру конструктора и подставляет правильный тип.

Если зависимость не зарегистрирована — `NoDefinitionFoundException` при первом запросе (или при `checkModules()` в тесте).

### Что мешает "забыть" что-то зарегистрировать?

Koin сам не проверяет граф зависимостей на этапе сборки. Но есть `checkModules()`:
```kotlin
fun main() {
    startKoin { modules(dataModule, presentationModule) }.checkKoinModules()
}
```
— в unit-тесте прогоняет все определения и падает, если чего-то не хватает.

Hilt/Dagger делают это на этапе компиляции (плюс генерации), Koin — в рантайме через тест.

### `androidContext()` — где он живёт

Технически Koin (артефакт `koin-core`) не знает, что такое `Context`. Это Android-понятие.

Артефакт `koin-android` добавляет:
1. **DSL-функцию `androidContext(app: Application)`** в блок `startKoin { ... }`. Она регистрирует переданное `app` (т.е. `Context`) как синглтон в Koin.
2. **DSL-функцию `androidContext()` в `Module`-scope**. Она достаёт зарегистрированный `Context` через `get<Context>()`.

То есть «протокол»:

```kotlin
// 1. При старте — кладём Context в граф:
startKoin {
    androidContext(this@App)   // ← регистрирует Context
    modules(...)
}

// 2. В модулях — достаём:
val androidDataModule = module {
    single { MusicScanner(androidContext()) }   // ← достаёт зарегистрированный Context
}
```

Без шага 1 в шаге 2 будет `NoDefinitionFoundException: No definition found for class:'Context'`.

### `startKoin { ... }` — что физически происходит

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(dataModule, androidDataModule, presentationModule)
        }
    }
}
```

`startKoin` создаёт `KoinApplication` (контейнер настроек) и `Koin` (рантайм с графом зависимостей). После выполнения:
- В глобальной точке (через `GlobalContext` объект) лежит ссылка на текущий `Koin`.
- Все определения из переданных модулей зарегистрированы в графе.
- Никаких объектов ещё не создано (single — lazy).

Дальше в любом месте приложения:
- `getKoin().get<Type>()` — достать зависимость напрямую.
- `class X : KoinComponent { val y: Y by inject() }` — через делегат `inject`.
- В Compose — `koinViewModel()`.

`startKoin` нельзя вызывать дважды в одном процессе — упадёт с `KoinAppAlreadyStartedException`. Поэтому стандартное место — `Application.onCreate()`, который Android вызывает один раз при старте процесса.

### `koinViewModel()` vs `viewModel()`

`koinViewModel()` (из `koin-compose-viewmodel`) — это обёртка над `viewModel()` из Compose. Под капотом:

```kotlin
@Composable
inline fun <reified T : ViewModel> koinViewModel(...): T {
    return viewModel<T>(factory = KoinViewModelFactory(...))
}
```

То есть он использует стандартный механизм Compose + Lifecycle (`androidx.lifecycle.viewmodel.compose.viewModel`), но передаёт ему **factory из Koin**. Factory знает, как через граф зависимостей собрать ViewModel: для каждого параметра конструктора зовёт `get()`.

Что значит «один на дестинацию»: `viewModel()` использует `ViewModelStore` текущего `LocalViewModelStoreOwner`. В навигационных компонентах эта область — `NavBackStackEntry` (для `composable<...>`) или Activity. Пока эта область жива, ViewModel живёт. Когда область умирает (свайп с экрана, выход с дестинации) — `onCleared()` вызывается, ViewModel освобождается.

Если случайно использовать `viewModel()` (без `koin`) с ViewModel'ом, имеющим непустой конструктор — `RuntimeException: Cannot create an instance of class TracksViewModel`. Compose сам не знает, как собирать аргументы; нужна factory. `koinViewModel()` это и решает.

### Почему конструктор-инъекция, а не field-инъекция

Koin поддерживает оба варианта:

```kotlin
// Конструктор-инъекция (рекомендуется):
class TracksViewModel(private val repo: TracksRepository) : ViewModel() { ... }

// Field-инъекция через KoinComponent:
class TracksViewModel : ViewModel(), KoinComponent {
    private val repo: TracksRepository by inject()
}
```

Конструктор лучше потому, что:

1. **Тестируемость.** В тесте создаёшь `TracksViewModel(FakeRepository())` — никакого Koin не нужно. С field-инъекцией пришлось бы стартовать Koin в тесте (или мокать `KoinComponent`).
2. **Явность.** По конструктору видно, что нужно классу. Field-инъекция «прячет» зависимости — посмотришь на конструктор, он пустой, но при инстанцировании магически подцепляются поля.
3. **Compile-time safety.** Если конструктор требует `TracksRepository`, а его нет в графе — ошибка при сборке ViewModel. Field — runtime.
4. **Цикличные зависимости видны раньше.** Если A нужен B, B нужен A — конструкторная инъекция упадёт сразу при `singleOf`. Field может «работать» до первого `inject()`.

Field-инъекция через `KoinComponent` оставлена для случаев, когда конструктор недоступен (Android-системные классы вроде `BroadcastReceiver`, которые Android создаёт сам).

### `runCatching` в onEvent — ловушка с `CancellationException`

В файле 06 мы будем активно использовать `runCatching { ... }` для обработки ошибок в ViewModel. Здесь стоит дать предупреждение, потому что Koin часто используется с тем же паттерном.

```kotlin
runCatching { tracksRepository.refresh() }
    .onSuccess { ... }
    .onFailure { ... }
```

`runCatching` ловит **все** `Throwable`, включая `CancellationException`. Это — баг в корутинном коде.

`CancellationException` — это сигнал «отмени корутину» (например, `viewModelScope` отменяется при `onCleared`). Этот сигнал должен **пробрасываться вверх**, иначе корутина не остановится. Если `runCatching` его перехватит и положит в `onFailure { it.message ?: "ошибка" }`, ты увидишь снэкбар «JobCancellation» каждый раз, когда юзер уходит с экрана во время загрузки.

Правильный паттерн:

```kotlin
runCatching { tracksRepository.refresh() }
    .onFailure { e -> if (e is CancellationException) throw e else _error.value = e.message }
    .onSuccess { ... }
```

Или используй `try/catch` с явным `catch (e: Exception)` (он не ловит `CancellationException`, потому что `CancellationException` происходит от `Throwable`/`Error`-ветки в kotlinx.coroutines... на самом деле он `Exception`, но это исключительная договорённость).

В стандартной библиотеке есть `runCatching`'оподобный helper для корутин, который сам пробрасывает `CancellationException`, но он не в kotlinx.coroutines, а нужно писать руками или взять из community-библиотек. Для гайда — помни про это сам.

Подробнее в `06-VIEWMODELS_AND_STATE.md`, разделе про `runCatching`.

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
