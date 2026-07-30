# 05. Dependency Injection — Koin

## Зачем

К этому моменту у нас уже есть: `MusicScanner`, `TracksRepositoryImpl`, `AlbumsRepositoryImpl`, `UserAlbumsRepositoryImpl`, `AudioPlayer`, `AppDatabase`, а дальше будут `TracksViewModel`, `AlbumsViewModel`, `PlayerViewModel`. Кто-то должен всё это создавать и **отдавать** в места, где это нужно.

Плохой вариант:
```kotlin - пример (как НЕ надо, не писать)
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

1. Подключим Koin через BOM.
2. Общий `dataModule` с репозиториями, у которых нет платформенных зависимостей.
3. Platform-specific `androidDataModule` / `iosDataModule` с `Context`-зависимыми вещами.
4. Пустой пока `presentationModule` — заполним в главе 06.
5. Сборщик `getSharedModule()` в `:shared` и точка входа `startKoin` в `Application`.
6. Разберём (только чтение, без написания кода!), как ViewModel будет попадать в Compose через `koinViewModel()` — сами экраны и ViewModel'и пишутся в главах 06–07.

**Код в этой главе пишется только в Шагах 1–6.** После Шага 6 проект должен собираться без ошибок. Шаги 7–8 — объяснение, ради чего всё это настраивалось; код там показан для понимания и для написания не предназначен.

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
└── PresentationModule.kt                 (новый, пока пустой)

shared/src/
├── commonMain/kotlin/org/example/mp3player/shared/di/
│   └── GetSharedModule.kt                (новый, expect + сборщик)
├── androidMain/kotlin/org/example/mp3player/shared/di/
│   └── PlatformModule.kt                 (новый, actual)
└── iosMain/kotlin/org/example/mp3player/shared/di/
    └── PlatformModule.kt                 (новый, actual)

composeApp/src/androidMain/
├── kotlin/org/example/mp3player/App.kt   (новый, Application + startKoin)
└── AndroidManifest.xml                   (обновить: android:name=".App")
```

---

## Реализация

> **Как отличать код «для проекта» от «для понимания».** В этой главе два вида блоков кода:
>
> 1. **Код для проекта** — блок начинается просто с `` ```kotlin ``, а первая строка внутри — комментарий с путём к файлу (например `// shared/data/src/.../DataModule.kt`). Такой блок целиком пишется/копируется в указанный файл.
>
> 2. **Пример или иллюстрация** — в заголовке блока написано `` ```kotlin - пример `` или `` ```kotlin - иллюстрация ``. Такой код **никуда писать не нужно** — это либо внутренности библиотеки (уже написаны авторами Koin), либо «как не надо», либо превью кода из будущих глав. Только для чтения.

### Шаг 1 — Зависимости

Koin делится на артефакты: `koin-core` (ядро, KMP), `koin-android` (расширения с `Context`/`Application`), `koin-compose` (получение зависимостей в `@Composable`), `koin-compose-viewmodel` (factory для ViewModel в Compose).

Добавляем версии и артефакты:

```toml
# gradle/libs.versions.toml
[versions]
koin = "4.2.1"

[libraries]
koin-bom = { group = "io.insert-koin", name = "koin-bom", version.ref = "koin" }
koin-core = { group = "io.insert-koin", name = "koin-core" }
koin-android = { group = "io.insert-koin", name = "koin-android" }
koin-compose = { group = "io.insert-koin", name = "koin-compose" }
koin-compose-viewmodel = { group = "io.insert-koin", name = "koin-compose-viewmodel" }
```

**Обрати внимание: у артефактов нет `version.ref`, только у `koin-bom`.** BOM (Bill of Materials) — это специальный «пустой» артефакт, который содержит список согласованных версий целого семейства библиотек. Подключаешь BOM один раз, дальше пишешь артефакты без версий — Gradle подставит их из BOM.

Зачем: у Koin четыре артефакта, и они должны быть одной версии. Без BOM легко получить `koin-core:4.2.1` и `koin-compose:4.1.0` — соберётся, а упадёт в рантайме с `NoSuchMethodError`. С BOM рассинхрон невозможен: поднял версию в одном месте — обновилось всё.

Подключаем по модулям — в `data` (ядро + Android-расширения):

```kotlin
// shared/data/build.gradle.kts
commonMain {
    dependencies {
        implementation(project.dependencies.platform(libs.koin.bom))
        implementation(libs.koin.core)
    }
}
androidMain {
    dependencies {
        implementation(libs.koin.android)
    }
}
```

`project.dependencies.platform(...)` — это и есть «подключить BOM». Слово `platform` — гредловский термин для такого артефакта.

В `presentation` (ядро + Compose-обёртки):

```kotlin
// shared/presentation/build.gradle.kts
commonMain {
    dependencies {
        implementation(project.dependencies.platform(libs.koin.bom))
        implementation(libs.koin.core)
        implementation(libs.koin.compose)
        implementation(libs.koin.compose.viewmodel)
    }
}
```

В `composeApp` (для `startKoin` в `Application`):

```kotlin
// composeApp/build.gradle.kts
androidMain.dependencies {
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)
}
```

### Шаг 2 — Data-модуль (common-часть)

Модули Koin — это «инструкции по сборке зависимостей». Создаём общую часть data-модуля: репозитории `TracksRepositoryImpl` и `AlbumsRepositoryImpl`, у которых нет platform-specific зависимостей.

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/di/DataModule.kt
package org.example.mp3player.data.di

import org.example.mp3player.data.repository.AlbumsRepositoryImpl
import org.example.mp3player.data.repository.TracksRepositoryImpl
import org.example.mp3player.domain.repository.AlbumsRepository
import org.example.mp3player.domain.repository.TracksRepository
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

```kotlin - иллюстрация (сигнатура из библиотеки Koin, не писать)
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

```kotlin - пример (сравнение двух форм записи, не писать)
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

import org.example.mp3player.core.audio.player.AudioPlayer
import org.example.mp3player.core.audio.scanner.MusicScanner
import org.example.mp3player.data.database.AppDatabase
import org.example.mp3player.data.database.dao.UserAlbumsDao
import org.example.mp3player.data.repository.UserAlbumsRepositoryImpl
import org.example.mp3player.domain.repository.UserAlbumsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val androidDataModule = module {
    single { AppDatabase.build(androidContext()) }
    single<UserAlbumsDao> { get<AppDatabase>().userAlbumsDao() }

    singleOf(::MusicScanner)
    single { AudioPlayer(androidContext()) }

    single<UserAlbumsRepository> { UserAlbumsRepositoryImpl(get()) }
}
```

`singleOf(::MusicScanner)` вместо `single { MusicScanner(androidContext()) }` — обе формы рабочие. `singleOf` короче и сам разрешит `Context` через граф (он там есть — см. про `androidContext()` ниже). Явная форма `single { ... }` нужна там, где параметр не берётся из графа напрямую — как у `AppDatabase.build(...)`, которое вообще не конструктор, а фабричный метод.

**А вот `AudioPlayer` — намеренно через `single { ... }`, и это важно.** Его Android-конструктор из главы 04 выглядит так:

```kotlin - напоминание из главы 04 (Шаг 7)
actual class AudioPlayer(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
)
```

Второй параметр имеет **значение по умолчанию**, и вот тут `singleOf` ломается: **конструкторный DSL Koin не знает про значения по умолчанию**. `::AudioPlayer` для компилятора это `KFunction2<Context, CoroutineScope, AudioPlayer>` — два параметра, значит `singleOf` подберёт двухаргументную перегрузку и честно вызовет `get<Context>()` и `get<CoroutineScope>()`. `CoroutineScope` в графе не зарегистрирован → падение при первом обращении к плееру:

```
NoDefinitionFoundException: No definition found for type 'kotlinx.coroutines.CoroutineScope'
```

Коварство в том, что **компилируется это без единого предупреждения**, а падает только в рантайме и только когда кто-то реально запросит `AudioPlayer` — то есть на этапе главы 06, когда уже забудешь, что менял DI.

Правило: **есть параметр со значением по умолчанию → пиши `single { ... }` явно.** `single { AudioPlayer(androidContext()) }` передаёт только `context`, а `scope` берётся из дефолта, как и задумано.

**По той же причине `UserAlbumsRepositoryImpl` регистрируется через `single<UserAlbumsRepository> { ... }`.** Его конструктор из главы 03 (Шаг 8):

```kotlin - напоминание из главы 03 (Шаг 8)
class UserAlbumsRepositoryImpl(
    private val dao: UserAlbumsDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : UserAlbumsRepository
```

`clock` с дефолтом → `singleOf(::UserAlbumsRepositoryImpl)` полез бы искать в графе `Function0<Long>` и упал бы при первом открытии вкладки «Мои альбомы» (глава 07). Явная форма `single<UserAlbumsRepository> { UserAlbumsRepositoryImpl(get()) }` передаёт только `dao`, а `clock` остаётся дефолтным.

Заодно заметь разницу в записи привязки к интерфейсу: у `singleOf` это отдельный блок `{ bind<X>() }`, а у `single` — параметр типа: `single<UserAlbumsRepository> { ... }`. Смысл один и тот же — «зарегистрируй под этим типом».

Как отличить заранее, не дожидаясь падения: открой конструктор класса и посмотри, есть ли хоть у одного параметра `= ...`. Есть — `single { ... }`. Нет — можно `singleOf`.

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

```kotlin - пример (фрагмент для объяснения, не писать)
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

import org.example.mp3player.core.audio.player.AudioPlayer
import org.example.mp3player.core.audio.scanner.MusicScanner
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val iosDataModule = module {
    singleOf(::MusicScanner)
    singleOf(::AudioPlayer)
    // UserAlbumsRepository — отдельная задача, пока iOS не используем.
}
```

Конструкторы без параметров — у iOS-`actual` нет `Context`. `UserAlbumsRepositoryImpl` пропущен: на iOS его реализация будет через что-то другое (Core Data / SQLDelight), мы пока этим не занимаемся.

### Шаг 5 — Presentation-модуль

Здесь будут регистрироваться все ViewModel'и. Это `commonMain` — KMP-модуль, никакой Android-специфики.

**Важно: сами ViewModel'и ещё не написаны** — они появятся в главе 06. Если зарегистрировать их сейчас, файл будет весь в ошибках «Unresolved reference». Поэтому создаём модуль **пустым**, а строки регистрации оставляем закомментированными — будешь раскомментировать по одной по мере написания каждой ViewModel в главе 06.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/di/PresentationModule.kt
package org.example.mp3player.presentation.di

import org.koin.dsl.module

val presentationModule = module {
    // ViewModel'и пишутся в главе 06. По мере создания каждой —
    // раскомментируй её строку и добавь import (Alt+Enter в Android Studio):
    //
    // viewModelOf(::TracksViewModel)
    // viewModelOf(::AlbumsViewModel)
    // viewModelOf(::AlbumDetailsViewModel)
    // viewModelOf(::PlayerViewModel)
    // viewModelOf(::UserAlbumsViewModel)
}
```

К концу главы 06 файл будет выглядеть так (это **превью конечного результата**, не пиши его сейчас):

```kotlin - пример (превью из главы 06, сейчас не писать)
package org.example.mp3player.presentation.di

import org.example.mp3player.presentation.albumdetails.AlbumDetailsViewModel
import org.example.mp3player.presentation.albums.AlbumsViewModel
import org.example.mp3player.presentation.player.PlayerViewModel
import org.example.mp3player.presentation.tracks.TracksViewModel
import org.example.mp3player.presentation.useralbums.UserAlbumsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::TracksViewModel)
    viewModelOf(::AlbumsViewModel)
    viewModelOf(::UserAlbumsViewModel)
    viewModelOf(::PlayerViewModel)

    // У AlbumDetailsViewModel первый параметр — albumId из навигации,
    // его не взять из графа. Разберём в главе 06 (3.3).
    viewModel { (albumId: String) -> AlbumDetailsViewModel(albumId, get(), get()) }
}
```

`viewModelOf(::X)` — то же что `singleOf(::X)`, но с другим scope: один экземпляр на жизненный цикл Compose-дестинации (или Activity). Когда дестинация уходит со стека — `onCleared()` ViewModel вызывается, экземпляр освобождается.

### Шаг 6 — Сборка модулей в `:shared` + `startKoin`

Осталось соединить всё вместе. Проблема: `androidDataModule` существует только в Android-сборке, `iosDataModule` — только в iOS-сборке. Если `composeApp` перечислит их напрямую, кросс-платформенность сломается. Решаем тем же `expect/actual`, только для модуля Koin.

Сначала — `expect`-функция и сборщик в `:shared`:

```kotlin
// shared/src/commonMain/kotlin/org/example/mp3player/shared/di/GetSharedModule.kt
package org.example.mp3player.shared.di

import org.example.mp3player.data.di.dataModule
import org.example.mp3player.presentation.di.presentationModule
import org.koin.core.module.Module

/** Platform-specific модуль: androidDataModule или iosDataModule. */
expect fun getPlatformModule(): Module

fun getSharedModule(): List<Module> = listOf(
    dataModule,
    presentationModule,
    getPlatformModule(),
)
```

Дальше — по одной строке на платформу:

```kotlin
// shared/src/androidMain/kotlin/org/example/mp3player/shared/di/PlatformModule.kt
package org.example.mp3player.shared.di

import org.example.mp3player.data.di.androidDataModule
import org.koin.core.module.Module

actual fun getPlatformModule(): Module = androidDataModule
```

```kotlin
// shared/src/iosMain/kotlin/org/example/mp3player/shared/di/PlatformModule.kt
package org.example.mp3player.shared.di

import org.example.mp3player.data.di.iosDataModule
import org.koin.core.module.Module

actual fun getPlatformModule(): Module = iosDataModule
```

**`expect fun`, а не `expect class`.** Тут нам не нужен тип с разными конструкторами — нужна одна функция, возвращающая разное значение на разных платформах. `expect`/`actual` работает и для функций, и для свойств, и для typealias, не только для классов.

Теперь точка входа. Для Koin это `Application.onCreate()` — место, куда Android отдаёт управление при старте процесса, до создания любой Activity.

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/App.kt
package org.example.mp3player

import android.app.Application
import org.example.mp3player.shared.di.getSharedModule
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
            modules(getSharedModule())
        }
    }
}
```

`modules(...)` перегружен и для `vararg Module`, и для `List<Module>` — поэтому список из `getSharedModule()` передаётся как есть.

Выигрыш: `composeApp` знает ровно один символ — `getSharedModule()`. Добавишь завтра `networkModule` — правка в одном файле, точка входа не меняется. И iOS-точка входа (`MainViewController`) вызовет ту же функцию.

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

### Шаг 7 — Как ViewModel попадёт в Compose (только чтение — кода не пишем)

> **В этом шаге ничего не пишем.** Код экрана `TracksScreen.kt` создаётся в **главе 06**, после того как будет написана `TracksViewModel`. Если попытаться написать его сейчас — Android Studio зальёт всё ошибками «Unresolved reference: TracksViewModel», потому что этого класса ещё не существует. Здесь мы только смотрим, **ради чего** настраивали Koin.

Вот так в главе 06 будет выглядеть получение ViewModel из Koin в Composable (превью, не пиши):

```kotlin - пример (превью из главы 06, сейчас не писать)
@Composable
fun TracksScreen(
    onOpenPlayer: () -> Unit,
    viewModel: TracksViewModel = koinViewModel(),   // ← вся магия в одной строке
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // ... UI экрана
}
```

`viewModel: TracksViewModel = koinViewModel()` — параметр со значением по умолчанию. Вызывающий код не передаёт ViewModel — Koin сам создаст `TracksViewModel` и подставит в её конструктор `TracksRepository`, `AudioPlayer` — что бы ни потребовалось. Именно поэтому мы в Шаге 5 завели `presentationModule`: `koinViewModel()` найдёт там регистрацию `viewModelOf(::TracksViewModel)` и по ней соберёт объект.

**`koinViewModel()` vs `viewModel()`.** `koinViewModel()` — это **готовая функция из библиотеки** `koin-compose-viewmodel` (мы подключили её в Шаге 1). Ты её **не пишешь** — только импортируешь: `import org.koin.compose.viewmodel.koinViewModel`.

Чтобы понять, чем она отличается от стандартной `viewModel()` из Compose, заглянем в её исходники. Упрощённо внутри библиотеки написано примерно так (**это код библиотеки Koin — НЕ копируй его в проект**, он уже написан авторами Koin):

```kotlin - иллюстрация (внутренности библиотеки Koin, НЕ копировать)
// внутренности библиотеки koin-compose-viewmodel (упрощённо) — только для чтения
@Composable
inline fun <reified T : ViewModel> koinViewModel(...): T {
    return viewModel<T>(factory = KoinViewModelFactory(...))
}
```

То есть `koinViewModel()` — тонкая обёртка: использует стандартный механизм Compose + Lifecycle (`viewModel()`), но передаёт ему **factory из Koin**. Factory знает, как через граф зависимостей собрать ViewModel: для каждого параметра конструктора зовёт `get()`.

«Один на дестинацию» означает: `viewModel()` использует `ViewModelStore` текущего `LocalViewModelStoreOwner`. В навигационных компонентах эта область — `NavBackStackEntry` (для `composable<...>`) или Activity. Пока область жива — ViewModel живёт. Уходит — `onCleared()`.

Если случайно использовать `viewModel()` (без `koin`) с ViewModel'ом, имеющим непустой конструктор — `RuntimeException: Cannot create an instance of class TracksViewModel`. Compose сам не знает, как собирать аргументы; нужна factory. `koinViewModel()` это и решает.

**Почему конструктор-инъекция, а не field-инъекция.** Koin поддерживает оба варианта:

```kotlin - пример (сравнение двух подходов, не писать)
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

### Шаг 8 — Вызов из не-Composable мест (только чтение — кода не пишем)

Тоже справочный шаг — пригодится в следующих главах. Иногда надо получить зависимость вне Composable (например, в `Application.onCreate`):

```kotlin - пример (справочный, пригодится в следующих главах)
import org.koin.java.KoinJavaComponent.getKoin

val audioPlayer: AudioPlayer = getKoin().get()
```

Или если класс реализует `KoinComponent`:

```kotlin - пример (справочный, пригодится в следующих главах)
class SomeHelper : KoinComponent {
    private val repo: TracksRepository by inject()
}
```

Но **в ViewModel инжекть через конструктор**, не через `inject()` — см. объяснение в Шаге 7.

> **Внимание про `runCatching` в обработке ошибок.** Когда будешь в `06-VIEWMODELS_AND_STATE.md` писать try/catch на корутинных вызовах через Koin — не используй `runCatching { ... }` без фильтра. Он ловит **все** `Throwable`, включая `CancellationException` (сигнал отмены корутины — `viewModelScope` отменяется при `onCleared`). Этот сигнал должен пробрасываться вверх. Правильный паттерн: `runCatching { ... }.onFailure { if (it is CancellationException) throw it else ... }`. Подробнее в файле 06.

---

## Чек-лист: что должно быть к концу главы

Написано и компилируется без ошибок:

- [ ] `gradle/libs.versions.toml` — BOM + 4 артефакта Koin (Шаг 1)
- [ ] `shared/data/build.gradle.kts`, `shared/presentation/build.gradle.kts`, `composeApp/build.gradle.kts` — зависимости подключены (Шаг 1)
- [ ] `DataModule.kt` (Шаг 2)
- [ ] `AndroidDataModule.kt` (Шаг 3)
- [ ] `IosDataModule.kt` (Шаг 4)
- [ ] `PresentationModule.kt` — **пустой** модуль с закомментированными `viewModelOf` (Шаг 5)
- [ ] `GetSharedModule.kt` + два `PlatformModule.kt` (Шаг 6)
- [ ] `App.kt` + `android:name` в манифесте (Шаг 6)

НЕ написано (и это правильно — появится в главе 06):

- `TracksViewModel` и остальные ViewModel'и
- `TracksScreen.kt` и остальные экраны
- Раскомментированные `viewModelOf(...)` в `PresentationModule.kt`

Проверка: собери проект (`Build → Make Project`) и запусти — приложение должно стартовать, в Logcat должны быть строки Koin (`[Koin] started ...`).

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

### 9. `singleOf`/`viewModelOf` для конструктора со значением по умолчанию
Конструкторный DSL Koin **игнорирует дефолтные значения** и требует каждый параметр из графа. `singleOf(::AudioPlayer)` при конструкторе `(Context, CoroutineScope = ...)` компилируется, но падает в рантайме: `No definition found for type 'CoroutineScope'`. То же с `UserAlbumsRepositoryImpl(dao, clock = ...)` → `No definition found for type 'Function0<Long>'`. Есть дефолт — пиши явно: `single { AudioPlayer(androidContext()) }` (Шаг 3).

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
