# Spec: Учебный гайд по реализации KMP music player

**Дата:** 2026-04-23
**Автор контекста:** студент (Kotlin/Compose на базовом уровне), целевой репозиторий — `D:\ai\mp3Player` (KMP-проект с `composeApp`, `shared/data`, `shared/domain`, `shared/presentation`).

---

## Цель

Создать учебный мультифайловый гайд в `docs/guide/`, по которому студент может пошагово реализовать локальный музыкальный плеер на Kotlin Multiplatform (Android-first) с функциями:

- Сканирование локальных аудиофайлов через MediaStore.
- Показ альбомов, автоматически сгруппированных из метаданных треков.
- Создание пользовательских альбомов/плейлистов (треки добавляются вручную, хранятся в Room).
- Воспроизведение через Media3/ExoPlayer с `MediaSessionService` (фон, уведомление, Bluetooth).
- Навигация между несколькими экранами через Navigation Compose.

Гайд должен быть самодостаточным: после прохождения студент понимает **что, зачем и как** реализовано в каждой подсистеме.

## Ограничения и рамки

- **Платформы:** Android-only реализация. iOS — только `expect`-заголовки в `commonMain` и пустые `actual`-заглушки (`TODO("iOS implementation")`) в `iosMain`, чтобы проект собирался и структура была готова.
- **Локализация приложения:** RU + EN через Compose Resources.
- **Язык гайда:** только RU.
- **Архитектура:** Clean Architecture (data/domain/presentation), DI через Koin, state через `StateFlow`. MVI, Voyager, Compose Destinations — явно отклонены.
- **Аудио:** только Media3/ExoPlayer. Старый `android.media.MediaPlayer` упоминается одним абзацем как "почему не его".
- **Не входит:** тесты, CI, iOS-реализация, Desktop, Compose Destinations.

## Целевая аудитория

- Студент, понимает базу Kotlin и Compose на уровне "читаю чужой Composable и понимаю что делает".
- Не знаком с: корутины в деталях, Flow-операторы, DI, Room + KSP, Media3, навигация, state-holders.
- Ожидает примеры сложнее "hello world": реальная обработка ошибок, `withContext(Dispatchers.IO)`, `StateFlow.stateIn`, sealed-иерархии состояний.

## Структура гайда

Директория: `docs/guide/`. 9 файлов с номерным префиксом. Номер = этап реализации.

| # | Файл | Содержание |
|---|------|------------|
| 00 | `00-ROADMAP.md` | 7 этапов реализации, чек-лист, финальная архитектура, словарь терминов, ссылки на остальные файлы. |
| 01 | `01-ARCHITECTURE.md` | Clean Arch в контексте проекта (зачем `data/domain/presentation`), видимость Gradle-модулей, суть `expect/actual` (как заготовка iOS), поток данных UI → ViewModel → UseCase → Repository → DataSource. |
| 02 | `02-PERMISSIONS_AND_SCAN.md` | Замена корневого `SCAN_MUSIC.md`. Фикс бага в `MusicScanner.android.kt:23` (лишняя `}`). Runtime permission `READ_MEDIA_AUDIO` на Android 13+ через `rememberLauncherForActivityResult`. Группировка `List<Track>` → `List<Album>`. `expect class MusicScanner` + iOS-заглушка. |
| 03 | `03-DATABASE_ROOM.md` | Room: entities `UserAlbum`, `UserAlbumTrackCrossRef`, DAO с `Flow<List<...>>`, `@Relation`, настройка KSP, миграции на будущее. Хранение позиции воспроизведения. |
| 04 | `04-PLAYBACK_MEDIA3.md` | ExoPlayer + `MediaSessionService` (фон, уведомление Play/Pause/Next, Bluetooth). Класс `AudioPlayer` как обёртка, `StateFlow<PlaybackState>`, регистрация сервиса в `AndroidManifest`. `expect class AudioPlayer` + iOS-заглушка. |
| 05 | `05-DI_KOIN.md` | Модули Koin для data/domain/presentation, `single`/`factory`/`viewModel`, `koinViewModel()` в Compose. Связка всего стека. |
| 06 | `06-VIEWMODELS_AND_STATE.md` | `UiState` как sealed class или data class, `MutableStateFlow` / `StateFlow`, `stateIn`, `onEvent(UiEvent)`. Почему не `mutableStateOf` в ViewModel. |
| 07 | `07-NAVIGATION_AND_SCREENS.md` | Navigation Compose KMP, type-safe routes через `@Serializable`. Примеры экранов: `TracksScreen` (LazyColumn), `AlbumsScreen` (Grid), `AlbumDetailsScreen`, `PlayerScreen` с SeekBar. Локализация RU+EN через Compose Resources. |
| 08 | `08-COVER_ART.md` | Переезд корневого `COVER_ART.md`. Coil3 + кэширование, плейсхолдер, получение обложки из `content://media/external/audio/albumart/...`. `expect class CoverArtReader` + iOS-заглушка. |

## Формат каждого файла

Каждый файл в `docs/guide/` имеет одинаковую структуру:

1. **Зачем** (1-2 абзаца) — мотивация, место в архитектуре.
2. **Что реализуем** — список файлов/классов, которые появятся по итогам.
3. **Реализация** — код в твоих пакетах (`org.example.mp3player.*`), разбит на шаги.
4. **Разбор** — построчное объяснение ключевых мест (корутины, Flow-операторы, Compose API).
5. **Подводные камни** — что отвалится, если забыть (например: не включил `kapt`/KSP, не зарегистрировал сервис, забыл `withContext`).
6. **Try yourself** — 2-3 упражнения на закрепление.
7. **Ссылки** — на другие файлы гайда и внешние доки.

## Зависимости между файлами

```
00-ROADMAP
  ├─ 01-ARCHITECTURE (база для всех)
  │   └─ 02-PERMISSIONS_AND_SCAN
  │       └─ 03-DATABASE_ROOM
  │           └─ 04-PLAYBACK_MEDIA3
  │               └─ 05-DI_KOIN (сводит всё что выше)
  │                   └─ 06-VIEWMODELS_AND_STATE
  │                       └─ 07-NAVIGATION_AND_SCREENS
  │                           └─ 08-COVER_ART (последний штрих)
```

Порядок чтения = порядок реализации. Каждый файл требует знания всех предыдущих.

## Изменения в существующих файлах

- `GUIDE.md` (корень): остаётся как вводная страница, в конец добавляется блок **"Пошаговый гайд по реализации"** со ссылкой на `docs/guide/00-ROADMAP.md`.
- `SCAN_MUSIC.md` (корень): **удаляется**, содержимое переезжает в `02-PERMISSIONS_AND_SCAN.md` с фиксом бага и добавлением permission-флоу и группировки в альбомы.
- `COVER_ART.md` (корень): **удаляется**, содержимое переезжает в `08-COVER_ART.md` с обновлением (Coil3, кэш).

## Изменения в коде проекта

В гайде явно помечается фикс бага: `shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt:23` — лишняя закрывающая `}` в строке `selection`. Код в проекте **не правим** в рамках написания гайда — гайд должен показать студенту, как это исправить самому.

## Критерии приёмки

- Все 9 файлов созданы в `docs/guide/`.
- Два устаревших файла (`SCAN_MUSIC.md`, `COVER_ART.md`) удалены из корня.
- `GUIDE.md` дополнен ссылкой на roadmap.
- В каждом файле есть все 7 секций стандартного формата.
- Примеры кода — в правильных пакетах проекта (`org.example.mp3player.data/domain/presentation`).
- Примеры кода покрывают реальные сценарии (обработка ошибок, IO-диспатчер, состояния загрузки), а не "hello world".
- Файлы, где применимо (02, 04, 08), содержат `expect`-декларацию в `commonMain` и `actual`-заглушку с `TODO("iOS implementation")` в `iosMain`.
- Гайд полностью на русском.

## Не делаем (явно отклонено)

- iOS-реализации (только заглушки).
- Desktop-поддержку.
- Unit-/UI-тесты (отдельная тема).
- MVI, Compose Destinations, Voyager.
- Реальную правку кода проекта (правка баги `MusicScanner.android.kt:23` — описываем как упражнение, а не делаем сами).
