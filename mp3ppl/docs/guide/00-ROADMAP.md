# 00. Roadmap — пошаговая реализация плеера

> **Как читать этот гайд:** файлы пронумерованы `01` → `08`. Номер = порядок изучения и реализации. Каждый следующий файл опирается на предыдущие, поэтому **не перепрыгивай**. Даже если кажется, что "это я уже знаю" — пробеги глазами, там могут быть важные для нашего проекта детали.

---

## Что мы строим

Локальный музыкальный плеер для Android с фоновым воспроизведением. Два типа коллекций:

- **Альбомы из метаданных** — автоматически группируются из ID3-тегов сканированных файлов. Изменить нельзя (как в Spotify).
- **Мои альбомы (плейлисты)** — пользователь создаёт сам: название, обложка, вручную добавляет треки. Хранится в локальной БД.

Приложение KMP (на будущее — iOS), но в рамках гайда реализуем только Android. Для кросс-платформенных мест оставляем **`expect`-заголовок** в `commonMain` и **`actual`-заглушку** с `TODO("iOS implementation")` в `iosMain`, чтобы проект собирался и структура была готова.

---

## Финальная архитектура

```
┌──────────────────────────────────────────────────────────────┐
│                         composeApp                            │
│                      (точка входа, MainActivity)              │
└────────────────────────────┬─────────────────────────────────┘
                             │ depends on
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
   ┌──────────┐        ┌──────────┐       ┌──────────────┐
   │ :shared: │        │ :shared: │       │ :shared:     │
   │   data   │◄──────►│  domain  │◄─────►│ presentation │
   └──────────┘        └──────────┘       └──────────────┘
      │                                          │
      │ MediaStore                               │ Compose UI
      │ Room DB                                  │ ViewModel
      │ Media3/ExoPlayer                         │ StateFlow
      │ File I/O                                 │ Navigation
      │                                          │
      └──── Koin связывает всё вместе ──────────┘
```

Три модуля в `shared/`:

| Модуль | Пакет | Ответственность |
|--------|-------|-----------------|
| `shared:data` | `org.example.mp3player.data` | MediaStore, Room, ExoPlayer, работа с файлами. Реализация интерфейсов из `domain`. |
| `shared:domain` | `org.example.mp3player.domain` | Модели (`Track`, `Album`), интерфейсы репозиториев, use cases. **Без зависимостей от Android/Compose/Room.** |
| `shared:presentation` | `org.example.mp3player.presentation` | Compose-экраны, ViewModel, `UiState`, навигация. |

Правило: **`data` и `presentation` зависят от `domain`, но не друг от друга.** `domain` не зависит ни от чего, кроме чистого Kotlin + корутин.

---

## Этапы реализации

Каждый этап = 1 файл гайда. Когда этап завершён — можешь запустить приложение и увидеть работающую часть фичи.

### Этап 1 — Архитектура (`01-ARCHITECTURE.md`)
Разбираемся, зачем Clean Architecture, как Gradle-модули видят друг друга, что такое `expect/actual` и куда что класть. Код почти не пишем — это теоретический фундамент.

**В конце:** ты понимаешь, почему `MusicScanner` лежит в `data`, `Track` в `domain`, а `TracksScreen` в `presentation`.

### Этап 2 — Сканирование + разрешения (`02-PERMISSIONS_AND_SCAN.md`)
Запрашиваем permission `READ_MEDIA_AUDIO`, сканируем MediaStore, получаем `List<Track>`, группируем в `List<Album>`. Пишем `expect class MusicScanner` в common + Android-реализацию + iOS-заглушку.

**В конце:** приложение показывает список реальных треков с твоего устройства и список альбомов, сгруппированных из метаданных.

### Этап 3 — База данных (`03-DATABASE_ROOM.md`)
Room для пользовательских альбомов: `UserAlbum` + `UserAlbumTrackCrossRef` (связь M:N), DAO с `Flow`, `@Relation` для eager-loading, настройка KSP.

**В конце:** можешь создать "Мой альбом", добавить/убрать треки, изменения наблюдаются через `Flow`.

### Этап 4 — Воспроизведение (`04-PLAYBACK_MEDIA3.md`)
Media3/ExoPlayer + `MediaSessionService`. Фоновое воспроизведение, уведомление с Play/Pause/Next, Bluetooth-кнопки. `expect class AudioPlayer` + Android-реализация + iOS-заглушка.

**В конце:** треки реально играют, можешь свернуть приложение — музыка продолжает играть, на экране блокировки управление.

### Этап 5 — DI (`05-DI_KOIN.md`)
Koin-модули для каждого слоя. `single`, `factory`, `viewModel`. Запуск `startKoin` в `MainActivity`. `koinViewModel()` в Compose.

**В конце:** все `new ClassName(...)` заменены на инъекцию, ViewModel получают зависимости автоматически.

### Этап 6 — ViewModel + State (`06-VIEWMODELS_AND_STATE.md`)
Паттерн `UiState` через data class / sealed class, `MutableStateFlow` → `StateFlow`, `stateIn`, функция `onEvent(UiEvent)`.

**В конце:** все экраны следуют единому паттерну: `ViewModel` выдаёт `StateFlow<UiState>`, экран подписывается через `collectAsStateWithLifecycle`.

### Этап 7 — Навигация + экраны (`07-NAVIGATION_AND_SCREENS.md`)
Navigation Compose, type-safe routes через `@Serializable`. Четыре экрана: `TracksScreen`, `AlbumsScreen`, `AlbumDetailsScreen`, `PlayerScreen`. Локализация RU/EN через Compose Resources.

**В конце:** законченное приложение: переходишь между экранами, стек кнопки "назад" работает правильно, интерфейс на двух языках.

### Этап 8 — Обложки (`08-COVER_ART.md`)
Coil3 для асинхронной загрузки, кэш обложек, плейсхолдер, получение из `content://media/external/audio/albumart/`. `expect class CoverArtReader`.

**В конце:** красивые обложки во всех списках и на экране плеера, placeholder если обложки нет.

---

## Чек-лист прогресса

Отмечай по мере выполнения — полезно видеть, сколько осталось.

- [ ] 01 — Понимаю Clean Arch и структуру модулей
- [ ] 02 — Треки с устройства видны в `TracksScreen`
- [ ] 02 — Альбомы из метаданных видны в `AlbumsScreen`
- [ ] 03 — Могу создать "мой альбом" и добавить в него трек
- [ ] 04 — Треки играют в фоне, уведомление видно на экране блокировки
- [ ] 05 — Все зависимости идут через Koin, нет ручных `new`
- [ ] 06 — Все экраны следуют паттерну `UiState` + `StateFlow`
- [ ] 07 — Навигация работает, back-stack корректный, UI на RU/EN
- [ ] 08 — Обложки загружаются с кэшем, placeholder на месте

---

## Словарь терминов

Быстрая шпаргалка. Каждый термин подробно объясняется в соответствующем файле гайда — в столбце «Подробно в» указан номер файла плюс маркер раздела (`Р` = `## Разбор`, `РС-N` = «Разбор по строкам» в Шаге N).

### Архитектура и сборка

| Термин | Коротко | Подробно в |
|--------|---------|-----------|
| **Clean Architecture** | Разделение на слои `data` / `domain` / `presentation` | 01 |
| **KMP / Kotlin Multiplatform** | Один код → несколько платформ | 01 |
| **`expect` / `actual`** | Compile-time-полиморфизм: общий заголовок + реализация по таргетам, без vtable | 01-Р, 02, 04, 08 |
| **Gradle-модуль** | Независимая единица сборки; `:` ↔ путь к директории | 01-Р |
| **Version Catalog (`libs.versions.toml`)** | Централизованный список версий и артефактов; `alias(libs.plugins.X)` | 01-Р |
| **`sourceSets { commonMain.dependencies { ... } }`** | Группы директорий компиляции для каждой target-платформы | 01-Р |

### Корутины и потоки данных

| Термин | Коротко | Подробно в |
|--------|---------|-----------|
| **Coroutine / корутина** | Кооперативная задача, которая отдаёт поток обратно в пул на suspend-точке | 02-РС-7 |
| **`suspend`** | Модификатор функции с неявным `Continuation`; вызов = потенциальная suspend-точка | 02-РС-7 |
| **`Dispatchers.Main` / `Default` / `IO`** | Пулы потоков под разные задачи (UI / CPU / блокирующие I/O) | 02-Р |
| **`withContext(Dispatchers.X)`** | Suspend-функция: переключиться, дождаться, вернуться | 02-Р |
| **`launch`** | Запустить новую корутину параллельно (не дожидаться) | 02-Р |
| **`Mutex` / `withLock`** | Корутинный замок: блокирует **корутину**, не поток; не реентрант | 02-РС-7 |
| **`Flow`** | Cold-поток: пока никто не collect — ничего не происходит | 02, 06-Р |
| **`StateFlow`** | Hot-поток с одним «текущим значением»; новый подписчик получает его сразу | 02-РС-7, 06-Р |
| **`SharedFlow`** | Hot-поток без текущего значения; для одноразовых событий | 06-Р |
| **`MutableStateFlow.value =`** | Атомарная публикация; conflated; встроен `distinctUntilChanged` | 02-РС-7 |
| **`asStateFlow()`** | Апкаст: тот же объект, но без setter снаружи (read-only обёртка) | 02-РС-7 |
| **`Flow.map` vs `List.map`** | Разные функции с одинаковым именем: оператор cold-flow vs синхронная коллекция | 02-РС-8 |
| **`combine(a, b, c) { ... }`** | Объединить flow; ждёт первый эмит каждого, потом пересчитывает на любой emit | 06-Р |
| **`stateIn`** | Оператор: cold `Flow<T>` → hot `StateFlow<T>` в указанном scope | 06-Р |
| **`SharingStarted.WhileSubscribed(timeout)`** | Подписка живёт, пока есть collector, плюс `timeout` мс после ухода последнего | 06-Р |
| **`viewModelScope`** | `SupervisorJob + Main.immediate`; отменяется в `ViewModel.onCleared()` | 06-Р |
| **`runCatching` в корутинах** | Ловит **в т.ч.** `CancellationException` — нужно явно пробрасывать его | 05-Р, 06-Р |

### Kotlin-фишки

| Термин | Коротко | Подробно в |
|--------|---------|-----------|
| **Property delegation (`by`)** | `var x by mutableStateOf(...)` — сахар над `getValue/setValue` | 02-РС-10 |
| **Constructor reference (`::Class`)** | Ссылка на конструктор как `KFunctionN`; используется `singleOf(::X)` | 05-Р |
| **`groupBy` / `mapIndexed` / `sumOf` / `firstOrNull`** | Стандартные операторы коллекций — каждый разобран на конкретных треках | 02-РС-8 |
| **`sortedBy { it.title.lowercase() }`** | Зачем `.lowercase()`: иначе `Z < a < Я < я` по Unicode | 02-РС-8 |
| **`?.use { }`** | `Closeable.use` = `try { ... } finally { close() }` | 02-Р |
| **`coerceAtLeast` / `coerceIn` / `takeIf`** | Clamp + условный `value-or-null` идиомы | 04-Р |
| **`run` / `let` / `apply` / `also`** | Scope-функции: this/it × возвращает блок/this | 04-Р |
| **Extension на nullable receiver** | `fun String?.orFallback(...)` — можно вызвать на `null` | 02-Р |

### MediaStore и сканирование

| Термин | Коротко | Подробно в |
|--------|---------|-----------|
| **MediaStore** | Системная БД Android со списком медиафайлов | 02 |
| **`Cursor` + `?.use`** | ContentResolver-ресурс с обязательным `close()` | 02-Р |
| **`Build.VERSION.SDK_INT`** | Runtime-проверка версии Android для совместимости | 02-Р |

### Room и БД

| Термин | Коротко | Подробно в |
|--------|---------|-----------|
| **Room** | ORM поверх SQLite, кодоген через KSP | 03 |
| **DAO (Data Access Object)** | Интерфейс с SQL-запросами; Room генерирует `_Impl` | 03-Р |
| **`@Entity` / `@PrimaryKey(autoGenerate)`** | `id = 0` как маркер «новая запись»; SQLite сам выдаст id | 03-Р |
| **`@Index`** | B-tree поверх колонки: O(log N) вместо O(N) для `WHERE` | 03-Р |
| **`@Embedded` + `@Relation` + `Junction`** | Many-to-many через junction-таблицу; два SQL + сшивка в Kotlin | 03-Р |
| **`@Transaction`** | Один transactional блок; обязателен с `@Relation` | 03-Р |
| **`Flow<List<X>>` в DAO** | InvalidationTracker подписывается на таблицы и реэмитит при изменениях | 03-Р |
| **`OnConflictStrategy.IGNORE/REPLACE/ABORT`** | Что делать при дубле PK/UNIQUE на `INSERT` | 03-Р |
| **`fallbackToDestructiveMigration`** | DROP всех таблиц при несовпадении версий — для разработки | 03-Р |
| **KSP (Kotlin Symbol Processing)** | Генератор кода; для KMP — `add("kspAndroid", ...)` | 03-Р |

### Воспроизведение

| Термин | Коротко | Подробно в |
|--------|---------|-----------|
| **Media3 / ExoPlayer** | Современная Android-библиотека для воспроизведения медиа | 04 |
| **`MediaSessionService`** | Foreground-сервис, в котором живёт плеер; интеграция с lockscreen/BT | 04-Р |
| **`MediaController.buildAsync()`** | Асинхронный коннект через Binder/IPC; возвращает `ListenableFuture` | 04-Р |
| **`AudioAttributes(USAGE_MEDIA)`** | Подсказка системе для аудиофокуса, Bluetooth, регулятора громкости | 04-Р |
| **`Player.Listener`** | Java-интерфейс с дефолтными методами; вызывается на main | 04-Р |

### DI

| Термин | Коротко | Подробно в |
|--------|---------|-----------|
| **DI (Dependency Injection)** | Паттерн: получать зависимости снаружи, не создавать внутри | 05 |
| **Koin** | Лёгкий DI-фреймворк для Kotlin | 05 |
| **`module { ... }`** | DSL-функция, лямбда с receiver `Module` | 05-Р |
| **`single { ... }` (lazy)** | Создаётся при первом `get()`, дальше кешируется | 05-Р |
| **`factory` / `viewModel`** | Новый экземпляр на каждый запрос / на dest-scope | 05-Р |
| **`singleOf(::X) { bind<Y>() }`** | Constructor-reference + регистрация под другим типом | 05-Р |
| **`androidContext()`** | DSL-helper из koin-android для получения зарегистрированного `Context` | 05-Р |
| **`koinViewModel()`** | Compose-обёртка: `viewModel()` + Koin-фактория | 05-Р |

### UI и навигация

| Термин | Коротко | Подробно в |
|--------|---------|-----------|
| **`UiState`** | Data/sealed class — единый снимок состояния экрана | 06 |
| **`UiEvent`** | Sealed-иерархия: события от UI к ViewModel (клик, ввод) | 06 |
| **`mutableStateOf` + `remember` + `by`** | Три механизма Compose-state в одной строке | 02-РС-10 |
| **`LaunchedEffect(key)`** | Запустить корутину при появлении/смене ключа; `Unit` = один раз | 06-Р |
| **`collectAsStateWithLifecycle`** | Подписка на flow, отменяется при STOPPED — экономит CPU/батарею | 06-Р |
| **`collectLatest`** | Отменяет обработку предыдущего эмита при новом — для дебаунса | 06-Р |
| **Computed property** | `val x get() = ...` — не поле, а функция; пересчёт на каждый доступ | 06-Р |
| **Navigation Compose** | Библиотека Jetpack для навигации между Composable-экранами | 07 |
| **Type-safe routes (`@Serializable`)** | Маршруты — data class; плагин генерирует `KSerializer` | 07-Р |
| **`composable<T>` + `toRoute()`** | Reified-тип destination + декодирование аргументов | 07-Р |
| **`popUpTo + saveState + restoreState + launchSingleTop`** | Каждый флаг отдельно: что именно делает | 07-Р |
| **`Scaffold` + `PaddingValues`** | Slot-API + insets для system bars | 07-Р |
| **`GridCells.Adaptive(160.dp)`** | Адаптивная сетка: floor(W / minSize) колонок | 07-Р |
| **`items(..., key = { it.id })`** | Стабильный ключ для сохранения state при перестановках | 07-Р |
| **`enableEdgeToEdge` + `WindowInsets`** | Прозрачные system bars + ручной учёт insets | 07-Р |
| **Compose Resources** | Кросс-платформенная замена `strings.xml` для локализации | 07 |

### Coil

| Термин | Коротко | Подробно в |
|--------|---------|-----------|
| **Coil3** | KMP-библиотека загрузки изображений (Coil2 был Android-only) | 08 |
| **`ImageLoader`** | Глобальный объект Coil: кэши + executor + декодеры | 08-Р |
| **`SingletonImageLoader.setSafe { ... }`** | Lazy-провайдер глобального ImageLoader | 08-Р |
| **`MemoryCache.maxSizePercent(ctx, 0.25)`** | 25% от `Runtime.maxMemory()` — на 256MB heap → 64MB | 08-Р |
| **`crossfade(true)`** | Анимация alpha 0→1 при переходе Loading → Success (~100мс) | 08-Р |
| **`MediaMetadataRetriever`** | Нативный объект, требует `release()`/try-finally | 08-Р |
| **Coil cache key** | Идентификатор по `model`; для инвалидации — менять URI | 08-Р |
| **`PickVisualMedia`** | Системный photo picker без `READ_MEDIA_IMAGES` (API 33+) | 08-Р |
| **`MaterialTheme.colorScheme.surfaceVariant`** | Material 3 палитра, доступна через `CompositionLocal` | 08-Р |

---

## Что нужно установить перед стартом

1. **Android Studio** (Hedgehog или новее) — IDE.
2. **JDK 17+** — Android Studio идёт с комплектным JDK, отдельно обычно не нужен.
3. **Android SDK** — ставится через SDK Manager в Android Studio.
4. **Android-устройство или эмулятор** на API 26+. Для тестирования фонового воспроизведения лучше реальное устройство — на эмуляторе уведомления ведут себя странно.

Проверка, что окружение готово:

```bash
# В корне проекта
./gradlew :composeApp:assembleDebug
```

Если сборка прошла — можно начинать с `01-ARCHITECTURE.md`.

---

## Дальше

→ [`01-ARCHITECTURE.md`](./01-ARCHITECTURE.md)
