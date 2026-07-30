# Что я уже прошёл по проекту (контекст для ИИ-помощника)

Я студент, учу Kotlin/Compose. Делаю по гайду локальный музыкальный плеер (KMP-проект, но реально
пишу только Android; iOS — заглушки `TODO()`). Ниже — честный срез: что я руками написал и видел
работающим, а что только пролистал. **Не считай, что я знаю больше, чем тут написано.**

## Стек проекта

Kotlin Multiplatform, Compose Multiplatform, Media3/ExoPlayer, Room (+KSP), Koin, Navigation Compose,
корутины/Flow. Модули: `:composeApp` (вход), `:core` (аудио), `:shared:data`, `:shared:domain`,
`:shared:presentation`.

## Что я реально написал руками и запускал

**Модули и `expect/actual`.** Разложил проект по Gradle-модулям, правил `settings.gradle.kts`,
`libs.versions.toml` (version catalog), `sourceSets { commonMain/androidMain/iosMain }`. Сам, уже
после гайда, вынес аудио в отдельный модуль `:core` и переименовал `Track` → `AudioTrack` —
то есть перекладывание пакетов и починку импортов после рефакторинга делал руками.

**Разрешения + сканирование (MediaStore).** `READ_MEDIA_AUDIO` в манифесте и runtime-запрос.
`contentResolver.query(...)` с projection/selection/sortOrder, `?.use { cursor }`,
`getColumnIndexOrThrow`, фильтр `IS_MUSIC != 0` и по длительности, сортировка `COLLATE NOCASE`,
сборка `AudioTrack` и URI обложки из `content://media/external/audio/albumart/`. Всё внутри
`withContext(Dispatchers.IO)`.

**Room.** `@Entity` `UserAlbumEntity` + `UserAlbumTrackCrossRef` (связь many-to-many),
`@Embedded`/`@Relation` с junction, DAO с `Flow<List<...>>` и `suspend`-CRUD, `@Transaction`,
`OnConflictStrategy.IGNORE`, `@Query` с агрегатом (`COALESCE(MAX(position), -1)`), свой
default-метод в DAO (`reorderTracks` — удалить все cross-ref и пересоздать с новыми `position`).
Настраивал KSP; понял, что Room у нас живёт только в `androidMain`, не в common. Экспорт схемы
в `schemas/` тоже включал.

**Media3 / фоновое воспроизведение.** `MediaSessionService` в манифесте, `expect class AudioPlayer`
в common + android-`actual`. Внутри: `SessionToken` + `MediaController.Builder(...).buildAsync()`,
`future.addListener(..., MoreExecutors.directExecutor())`, подписка `Player.Listener`
(`onIsPlayingChanged`, `onMediaItemTransition`, `onTimelineChanged`), сборка `MediaItem` +
`MediaMetadata`, `setMediaItems/prepare/play`, `seekTo`, `seekToNext/PreviousMediaItem`, маппинг
своего `RepeatMode` в `Player.REPEAT_MODE_*`, опрос позиции корутиной с `delay(500ms)` и
`ensureActive()`. Музыка реально играла в фоне, уведомление видел.

**Koin.** Отдельный module на слой, `single`, `singleOf(::X) { bind<Y>() }`,
`single { get<AppDatabase>().userAlbumsDao() }`, `androidContext()`, `viewModelOf(::X)`,
`startKoin` в приложении, `koinViewModel()` в Compose.

**ViewModel + State.** `TracksViewModel`: `combine` четырёх flow (треки + запрос поиска + loading +
error) → `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue)`.
`data class TracksUiState` с computed-property `filteredTracks` (`val ... get() =`).
`sealed interface TracksEvent` + одна точка входа `onEvent(event)`. Одноразовые эффекты через
`MutableSharedFlow` (`OpenPlayer`, `ShowMessage`). `runCatching { }.onFailure { }` вокруг refresh.
`PlayerViewModel`: `audioPlayer.state.map { it.toUi() }.stateIn(...)`, форматирование времени.
В репозитории — `MutableStateFlow` + `Mutex().withLock` вокруг скана.

**Compose UI (частично).** `TracksScreen`: `collectAsStateWithLifecycle()`, `LaunchedEffect(Unit)`,
`effects.collectLatest`, `LazyColumn` + `itemsIndexed`, `OutlinedTextField` для поиска,
ветвление `when { loading / error / content }`.

**Навигация (частично).** Type-safe routes: `@Serializable data object/data class` + `interface Route`,
`NavHost` + `composable<T>`, `backStackEntry.toRoute()` для аргументов. `Scaffold` + `NavigationBar`,
определение выбранной вкладки через `currentBackStackEntryAsState()` + `destination.hierarchy` +
`hasRoute(...)`, навигация с `popUpTo(...) { saveState = true }` + `launchSingleTop` + `restoreState`.
Локализация через Compose Resources (`stringResource(Res.string.tab_tracks)`).

## Что я видел, но уверенности нет

- **Корутины/Flow в деталях.** Пользуюсь `combine`, `stateIn`, `WhileSubscribed`, `collectLatest`
  по образцу. Почему именно такой `started`, чем `SharedFlow` отличается от `StateFlow` на практике,
  и что `runCatching` глотает `CancellationException` — объяснить не смогу, разбирать надо заново.
- **`expect/actual` и KMP-механика.** Пишу по шаблону; как это устроено под капотом — смутно.
- **Room `@Relation`/junction.** Написал и заработало, но повторить с нуля без подсказки вряд ли смогу.
- **Media3.** Много кода взято из гайда почти как есть. Логику `MediaController` через IPC и жизненный
  цикл сервиса понимаю поверхностно.
- **Sealed-class UiState.** Есть закомментированный черновик `TracksUiStateV2` (Loading/Content/Error) —
  пробовал, но не довёл; сейчас в проекте плоский data class с флагами.

## Что я вообще не трогал

- Обложки / Coil3 (`ImageLoader`, кэш, `crossfade`, `MediaMetadataRetriever`, photo picker) — этап гайда
  ещё не начат.
- Тесты — их в проекте нет вообще, TDD не применял.
- iOS: `actual` — пустые `TODO("iOS implementation")`, ни разу не собирал под iOS.
- Оптимизация/производительность Compose (recomposition, stability) — не изучал.

## Текущее состояние кода (важно)

Проект **сейчас не компилируется**: я в середине этапа навигации. В `AppNavHost` и `PresentationModule`
уже есть ссылки на то, чего ещё нет — `AlbumsScreen`, `UserAlbumsScreen`, `AlbumDetailsScreen`,
`PlayerScreen` и их ViewModel. В `TracksScreen` не хватает `TrackRow`/`ErrorBanner`, и параметр
`snackbar: (String) -> Unit` вызывается как `snackbar.showSnackbar(...)` — это ошибка. В `MainScaffold`
колбэк `onSnackbar` пустой. Всё это — «доделать», а не «сломалось».

## Как со мной удобнее работать

Короткие конкретные шаги, не простыни текста. Примеры сложнее «hello world», но по одному за раз.
Если объясняешь то, что в списке «видел, но плаваю» — объясняй как в первый раз, не ссылайся на то,
что я это «уже прошёл».
