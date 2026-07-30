# 07. Навигация и экраны + локализация

## Зачем

К концу главы 06 у нас есть все ViewModel и один работающий экран (`TracksScreen`), прибитый гвоздями к `RootScreen`. Остальные четыре экрана не написаны, переключаться между ними нечем.

Требования к навигации:
- **Back stack** — системная кнопка «назад» возвращает на предыдущий экран.
- **Передача данных** — на `AlbumDetailsScreen` надо передать `albumId`.
- **Type safety** — компилятор должен проверить, что передаём правильные аргументы.
- **Не терять state** при переключении вкладок.
- **Deep links** (на будущее).

**Navigation Compose** (AndroidX) — стандарт для Compose-приложений. В KMP есть совместимая версия `org.jetbrains.androidx.navigation:navigation-compose`. Начиная с 2.8 она поддерживает **type-safe routes** через `@Serializable` data class — именно их мы и используем.

Плюс Material 3 `Scaffold` + `NavigationBar` дадут нижнее меню в 20 строк.

Плюс локализация через **Compose Resources** — аналог `strings.xml`, но кросс-платформенный.

---

## Что реализуем

Порядок снова строго последовательный: сначала инфраструктура (зависимости, строки, маршруты), потом все экраны по очереди, и только в конце — граф навигации, который их связывает. Так ни один файл не ссылается на несуществующий.

```
shared/presentation/src/commonMain/
├── composeResources/
│   ├── values/strings.xml                        (1 — ru, дефолт)
│   └── values-en/strings.xml                     (2 — en)
└── kotlin/org/example/mp3player/presentation/
    ├── navigation/Route.kt                       (3 — все маршруты)
    ├── common/
    │   ├── StateViews.kt                         (4 — обновляем: строки из ресурсов)
    │   └── CoverArt.kt                           (5 — заглушка обложки)
    ├── tracks/TracksScreen.kt                    (6 — обновляем: строки + слот)
    ├── albums/AlbumsScreen.kt                    (7 — новый, сетка)
    ├── albumdetails/AlbumDetailsScreen.kt        (8 — новый)
    ├── player/PlayerScreen.kt                    (9 — новый)
    ├── useralbums/
    │   ├── UserAlbumsScreen.kt                   (10 — новый)
    │   ├── CreateUserAlbumDialog.kt              (11 — новый)
    │   └── AddToUserAlbumDialog.kt               (15 — новый)
    ├── navigation/AppNavHost.kt                  (12 — граф)
    └── root/MainScaffold.kt                      (13 — нижнее меню + снэкбар)

shared/src/commonMain/.../shared/RootScreen.kt    (14 — подключаем MainScaffold)
```

Шаг 15 — последний: он дописывает `TracksViewModel` и `TracksScreen`, замыкая «добавить трек в мой альбом». Всё остальное к этому моменту уже написано, поэтому он идёт в самом конце.

---

## Реализация

### Шаг 1 — Зависимости

`gradle/libs.versions.toml`:

```toml
[versions]
navigationCompose = "2.9.2"          # KMP-версия, проверь актуальную
kotlinxSerialization = "2.3.10"      # версия компилятор-плагина, идёт в ногу с Kotlin
kotlinxSerializationJson = "1.10.0"  # версия runtime-библиотеки

[libraries]
androidx-navigation-compose = { group = "org.jetbrains.androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }

[plugins]
kotlinx-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlinxSerialization" }
```

> **Две разные версии — не опечатка.** `kotlinx-serialization` в `[plugins]` — это **компилятор-плагин**, его версия привязана к версии Kotlin. `kotlinx-serialization-json` в `[libraries]` — это **runtime-библиотека**, у неё своя нумерация (1.x). Путать их — классическая причина ошибки `Serializer for class is not found` при, казалось бы, подключённом плагине.

`shared/presentation/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinx.serialization)      // ← новое
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // ... то, что уже было ...
                implementation(libs.androidx.navigation.compose)   // ← новое
                implementation(libs.kotlinx.serialization.json)    // ← новое
                implementation(compose.materialIconsExtended)      // ← новое
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "org.example.mp3player.presentation.resources"
}
```

> **Про иконки.** Дальше в главе мы используем `Icons.Default.Album`, `MusicNote`, `PlaylistPlay`, `SkipPrevious`, `SkipNext`, `Pause`, `Delete`. Вместе с `material3` идёт только небольшой базовый набор (`Add`, `PlayArrow`, `KeyboardArrowDown`, `ArrowBack` — эти доступны без доп. зависимости). Всё остальное лежит в отдельной библиотеке **material-icons-extended**, поэтому её надо подключить строкой выше — иначе импорты `androidx.compose.material.icons.filled.*` не найдутся.
>
> Пиши именно `compose.materialIconsExtended` (accessor от compose-плагина). IDE покажет warning «deprecated — specify dependency directly» — это нормально, оно работает. **Не** заменяй на `implementation("org.jetbrains.compose.material:material-icons-extended:1.10.0")`: артефакт заморожен на версии 1.7.3, такой строки не существует, будет ошибка резолва.

`compose.resources { ... }` — блок настройки Compose Resources. `publicResClass = true` делает сгенерированный класс `Res` публичным (без него он `internal` и не виден из других модулей). `packageOfResClass` задаёт пакет, из которого ты будешь его импортировать.

После правки Gradle — **Sync Project with Gradle Files**, иначе `Res` не сгенерируется.

### Шаг 2 — Строки

Compose Resources ищет ресурсы в `src/commonMain/composeResources/`. Директория `values/` — дефолт, `values-<lang>/` — переводы.

`shared/presentation/src/commonMain/composeResources/values/strings.xml` (дефолт — русский):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Музыка</string>

    <string name="tab_tracks">Треки</string>
    <string name="tab_albums">Альбомы</string>
    <string name="tab_my_albums">Мои альбомы</string>

    <string name="tracks_search_hint">Поиск</string>
    <string name="tracks_empty_title">Нет музыки</string>
    <string name="tracks_empty_description">Добавь треки на устройство и обнови</string>
    <string name="tracks_no_results">Ничего не найдено</string>

    <string name="albums_empty">Альбомы появятся после сканирования</string>
    <string name="album_details_play_all">Слушать всё</string>
    <string name="album_details_not_found">Альбом не найден</string>
    <string name="album_tracks_count">Треков: %1$d</string>

    <string name="user_albums_empty_title">Создай свой первый альбом</string>
    <string name="user_albums_empty_description">Добавляй треки из библиотеки</string>
    <string name="user_albums_create">Создать альбом</string>
    <string name="user_album_delete">Удалить альбом</string>
    <string name="user_album_dialog_title">Новый альбом</string>
    <string name="user_album_dialog_name_hint">Название</string>
    <string name="user_album_dialog_create">Создать</string>
    <string name="user_album_dialog_cancel">Отмена</string>
    <string name="add_to_album">Добавить в альбом</string>
    <string name="add_to_album_title">В какой альбом?</string>
    <string name="add_to_album_empty">Сначала создай альбом на вкладке «Мои альбомы»</string>
    <string name="add_to_album_done">Добавлено</string>

    <string name="player_previous">Предыдущий</string>
    <string name="player_next">Следующий</string>
    <string name="player_play">Играть</string>
    <string name="player_pause">Пауза</string>
    <string name="player_close">Свернуть</string>
    <string name="player_nothing">Ничего не играет</string>

    <string name="action_back">Назад</string>
    <string name="action_retry">Попробовать ещё раз</string>
</resources>
```

`shared/presentation/src/commonMain/composeResources/values-en/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Music</string>

    <string name="tab_tracks">Tracks</string>
    <string name="tab_albums">Albums</string>
    <string name="tab_my_albums">My albums</string>

    <string name="tracks_search_hint">Search</string>
    <string name="tracks_empty_title">No music found</string>
    <string name="tracks_empty_description">Add tracks to your device and refresh</string>
    <string name="tracks_no_results">Nothing matches</string>

    <string name="albums_empty">Albums will appear after scanning</string>
    <string name="album_details_play_all">Play all</string>
    <string name="album_details_not_found">Album not found</string>
    <string name="album_tracks_count">Tracks: %1$d</string>

    <string name="user_albums_empty_title">Create your first album</string>
    <string name="user_albums_empty_description">Add tracks from your library</string>
    <string name="user_albums_create">Create album</string>
    <string name="user_album_delete">Delete album</string>
    <string name="user_album_dialog_title">New album</string>
    <string name="user_album_dialog_name_hint">Title</string>
    <string name="user_album_dialog_create">Create</string>
    <string name="user_album_dialog_cancel">Cancel</string>
    <string name="add_to_album">Add to album</string>
    <string name="add_to_album_title">Which album?</string>
    <string name="add_to_album_empty">Create an album on the “My albums” tab first</string>
    <string name="add_to_album_done">Added</string>

    <string name="player_previous">Previous</string>
    <string name="player_next">Next</string>
    <string name="player_play">Play</string>
    <string name="player_pause">Pause</string>
    <string name="player_close">Collapse</string>
    <string name="player_nothing">Nothing is playing</string>

    <string name="action_back">Back</string>
    <string name="action_retry">Try again</string>
</resources>
```

> **Три строки останутся неиспользованными — так и задумано.** `app_name` пригодится, когда дойдёшь до иконки и названия приложения. `album_details_not_found` и `add_to_album_done` — это тексты, которые сейчас захардкожены **внутри ViewModel** (`AlbumDetailsViewModel` в 06/3.3 и `TracksViewModel.addToAlbum()` в 06/1.4): ресурсы `Res.string.*` читаются только из `@Composable`, а ViewModel не Composable. Строки лежат в ресурсах заранее — под задание «вынести тексты эффектов и ошибок из ViewModel» (эффект несёт тип сообщения, экран переводит его в строку). Подробнее — в Шаге 15.

Использование:

```kotlin - пример (шаблон, применим ниже в каждом экране)
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.tracks_search_hint
import org.jetbrains.compose.resources.stringResource

Text(stringResource(Res.string.tracks_search_hint))
```

Сборка сгенерирует `Res.string.tracks_search_hint` автоматически. Язык выбирается системой (локаль устройства) — в коде настраивать ничего не надо.

**`%1$d` — позиционный аргумент.** `stringResource(Res.string.album_tracks_count, 12)` подставит `12` вместо `%1$d`. Цифра перед `$` — номер аргумента (с единицы), буква после — тип (`d` — целое, `s` — строка). Позиционная форма (а не просто `%d`) обязательна, если аргументов больше одного, и безопасна всегда: в другом языке порядок слов может отличаться, и переводчик переставит `%1$s` и `%2$s` местами.

> **Правильное «12 треков» по-русски.** `Треков: %1$d` — сознательный компромисс: он не требует склонений. Настоящее решение — `pluralStringResource` с формами `one/few/many/other`. Оставлено как задание в конце главы.

Если после сборки `Res.string.*` не находится — **Build → Clean Project**, потом пересобрать. Кодогенерация ресурсов не всегда подхватывает новые файлы инкрементально.

### Шаг 3 — Маршруты

Маршруты в Navigation Compose 2.8+ — **type-safe**: каждый экран это `@Serializable` data class или data object, навигация принимает не строку, а сам объект (`navController.navigate(AlbumDetailsRoute("42"))`). Это убирает классические баги старого API: опечатки в именах параметров, ручное доставание через `getString`, неработающий рефакторинг в IDE.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/navigation/Route.kt
package org.example.mp3player.presentation.navigation

import kotlinx.serialization.Serializable

/** Общий маркер: всё, куда можно навигировать. */
sealed interface Route

// Вкладки нижнего меню.
@Serializable data object TracksRoute : Route
@Serializable data object AlbumsRoute : Route
@Serializable data object UserAlbumsRoute : Route

// Экраны поверх вкладок.
@Serializable data class AlbumDetailsRoute(val albumId: String) : Route
@Serializable data object PlayerRoute : Route
```

Пять объявлений и один интерфейс — всё. Никаких «вспомогательных» иерархий: любая лишняя абстракция в маршрутах со временем расходится с реальным графом и начинает врать.

`sealed interface Route` нужен ровно для одного: в Шаге 13 у нас будет `data class BottomItem(val route: Route, ...)`, и `Route` даёт этому полю осмысленный тип вместо `Any`.

**Что плагин `kotlinx.serialization` делает в момент сборки.** Сама аннотация `@Serializable` ничего не делает — её обрабатывает **компилятор-плагин** (тот, что подключили в Шаге 1). Что он генерирует:

1. Для каждого `@Serializable`-класса создаётся companion object с методом `serializer()`, возвращающим `KSerializer<T>` — объект, умеющий кодировать/декодировать инстанс.
2. Этот `KSerializer` универсальный: работает с JSON, с бинарными форматами, с собственными.
3. Без плагина: класс помечен `@Serializable`, но `serializer()` не сгенерирован → `SerializationException: Serializer for class 'AlbumDetailsRoute' is not found`.

Navigation Compose 2.8+ использует этот `KSerializer`:
- `navController.navigate(AlbumDetailsRoute("123"))` → сериализация → URL-encoded строка → кладётся в back stack.
- `entry.toRoute<AlbumDetailsRoute>()` → достаёт строку из back stack → десериализация → возвращает `AlbumDetailsRoute`.

В Logcat можно увидеть фактический маршрут — что-то вроде `org.example.mp3player.presentation.navigation.AlbumDetailsRoute/123`. Это и есть закодированный data class.

### Шаг 4 — Локализуем `StateViews`

В главе 06 мы захардкодили строку `"Попробовать ещё раз"` в `ErrorBanner`. Теперь она приходит из ресурсов.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/common/StateViews.kt
// (меняется только ErrorBanner — LoadingBox и EmptyState остаются как есть)
package org.example.mp3player.presentation.common

// ... импорты из главы 06 ...
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.action_retry
import org.jetbrains.compose.resources.stringResource

@Composable
fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryText: String = stringResource(Res.string.action_retry),
) {
    // тело не меняется
}
```

**Значение по умолчанию, которое вызывает `@Composable`-функцию.** `stringResource(...)` — это `@Composable`, и вызывать её можно только из composable-контекста. Значения по умолчанию вычисляются **в теле вызывающей функции**, а `ErrorBanner` помечена `@Composable` — значит, всё легально.

Обрати внимание на порядок: `modifier` переехал **перед** `retryText`. Конвенция Compose — `modifier` идёт первым среди опциональных параметров, сразу после обязательных. Так вызывающий код чаще может обойтись позиционными аргументами.

`EmptyState` и `LoadingBox` не трогаем: они принимают все тексты снаружи, локализовать внутри нечего. Это признак хорошего переиспользуемого компонента — он не знает про конкретные строки приложения.

### Шаг 5 — `CoverArt` (заглушка обложки)

Обложки — тема главы 08. Но сетка альбомов и экран плеера нужны сейчас. Заводим компонент с правильной сигнатурой и временной реализацией: в главе 08 поменяем только его тело, ни один вызывающий экран трогать не придётся.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/common/CoverArt.kt
package org.example.mp3player.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Обложка альбома или трека.
 *
 * Пока рисует только плейсхолдер — параметр [data] не используется.
 * В главе 08 подключим Coil и загрузим реальную картинку, сигнатура не изменится.
 */
@Composable
fun CoverArt(
    data: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

**Размер задаётся снаружи через `modifier`**, а не параметром `size: Dp`. Это идиоматичнее: вызывающий пишет `CoverArt(..., modifier = Modifier.size(56.dp))` или `Modifier.aspectRatio(1f).fillMaxWidth()` — и компонент одинаково хорошо работает и в списке, и в сетке, и на весь экран плеера.

`MaterialTheme.colorScheme.surfaceVariant` — цвет Material 3 для «приподнятых» поверхностей. Он автоматически адаптируется к светлой и тёмной теме, поэтому плейсхолдер не будет белым пятном ночью. Доступен только внутри `@Composable` (это `CompositionLocal`) — в обычную константу его не сохранить.

### Шаг 6 — Обновляем `TracksScreen`

Меняем захардкоженные строки на ресурсы и добавляем параметр `modifier`. Полный файл:

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/tracks/TracksScreen.kt
package org.example.mp3player.presentation.tracks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.presentation.common.EmptyState
import org.example.mp3player.presentation.common.ErrorBanner
import org.example.mp3player.presentation.common.LoadingBox
import org.example.mp3player.presentation.common.TrackRow
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.tracks_empty_description
import org.example.mp3player.presentation.resources.tracks_empty_title
import org.example.mp3player.presentation.resources.tracks_no_results
import org.example.mp3player.presentation.resources.tracks_search_hint
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TracksScreen(
    onOpenPlayer: () -> Unit,
    onSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TracksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onEvent(TracksEvent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TracksEffect.OpenPlayer -> onOpenPlayer()
                is TracksEffect.ShowMessage -> onSnackbar(effect.text)
            }
        }
    }

    when (val current = state) {
        TracksUiState.Loading -> LoadingBox(modifier)

        is TracksUiState.Error -> ErrorBanner(
            message = current.errorText,
            onRetry = { viewModel.onEvent(TracksEvent.Refresh) },
            modifier = modifier,
        )

        is TracksUiState.Content -> TracksContent(
            state = current,
            onEvent = viewModel::onEvent,
            modifier = modifier,
        )
    }
}

@Composable
private fun TracksContent(
    state: TracksUiState.Content,
    onEvent: (TracksEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onEvent(TracksEvent.Search(it)) },
            placeholder = { Text(stringResource(Res.string.tracks_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        val tracks = state.filteredTracks
        when {
            state.tracks.isEmpty() -> EmptyState(
                title = stringResource(Res.string.tracks_empty_title),
                description = stringResource(Res.string.tracks_empty_description),
            )

            tracks.isEmpty() -> EmptyState(
                title = stringResource(Res.string.tracks_no_results),
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.id },
                ) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onEvent(TracksEvent.PlayTrack(index)) },
                    )
                }
            }
        }
    }
}
```

Добавился `modifier: Modifier = Modifier` — через него `MainScaffold` передаст отступы от `Scaffold` (Шаг 13). Без этого параметра список уехал бы под нижнее меню.

Порядок параметров: сначала обязательные (`onOpenPlayer`, `onSnackbar`), потом `modifier`, потом `viewModel` с дефолтом. `viewModel` идёт последним намеренно — это «служебный» параметр, который вызывающий код почти никогда не передаёт (только тесты и превью).

### Шаг 7 — `AlbumsScreen` (сетка)

Первый новый экран. `AlbumsViewModel` из главы 06 (2.2) не принимает событий, поэтому экран получается чисто отображающим.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/albums/AlbumsScreen.kt
package org.example.mp3player.presentation.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.domain.model.Album
import org.example.mp3player.presentation.common.CoverArt
import org.example.mp3player.presentation.common.EmptyState
import org.example.mp3player.presentation.common.LoadingBox
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.album_tracks_count
import org.example.mp3player.presentation.resources.albums_empty
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlbumsScreen(
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        AlbumsUiState.Loading -> LoadingBox(modifier)

        AlbumsUiState.Empty -> EmptyState(
            title = stringResource(Res.string.albums_empty),
            modifier = modifier,
        )

        is AlbumsUiState.Content -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            items(items = current.albums, key = { it.id }) { album ->
                AlbumCard(album = album, onClick = { onAlbumClick(album.id) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column {
            CoverArt(
                data = album.coverUri,
                contentDescription = album.title,
                cornerRadius = 0.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.album_tracks_count, album.trackCount),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
```

**`LazyVerticalGrid(GridCells.Adaptive(160.dp))` — как считается число колонок.** «Положи столько колонок, сколько влезет, при условии что каждая ≥ 160dp». Алгоритм при ширине `W`: `n = floor(W / 160)`, реальная ширина колонки `W / n`. Примеры:
- Телефон 360dp → 2 колонки по ~180dp.
- Ландшафт 720dp → 4 колонки по ~180dp.
- Планшет 800dp → 5 колонок по ~160dp.

Альтернатива — `GridCells.Fixed(n)`: ровно n колонок всегда. Удобно для фиксированного дизайна.

Подвох: `LazyVerticalGrid` нельзя помещать в родителя без ограничения высоты (внутрь `Column` без `Modifier.weight(1f)`) — упадёт с `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`.

**`aspectRatio(1f)` после `fillMaxWidth()`** — порядок важен. `fillMaxWidth()` фиксирует ширину по родителю, `aspectRatio(1f)` выводит из неё высоту. В обратном порядке `aspectRatio` не от чего считать.

`cornerRadius = 0.dp` для обложки внутри карточки: скругление уже даёт сама `Card`, второе скругление внутри выглядело бы как дырка в углу.

`Card(onClick = ...)` — у Material 3 `Card` действительно есть перегрузка с `onClick` (у `Row`/`Column` её нет, там нужен `Modifier.clickable { }`). В части версий Material 3 эта перегрузка помечена экспериментальной, поэтому стоит `@OptIn(ExperimentalMaterial3Api::class)`. Если твоя версия её уже стабилизировала, IDE покажет warning «unnecessary opt-in» — это безобидно, аннотацию можно убрать.

### Шаг 8 — `AlbumDetailsScreen`

Первый экран с аргументом навигации. Здесь применим `parametersOf`, о котором говорили в 06 (3.3).

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/albumdetails/AlbumDetailsScreen.kt
package org.example.mp3player.presentation.albumdetails

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.presentation.common.CoverArt
import org.example.mp3player.presentation.common.ErrorBanner
import org.example.mp3player.presentation.common.LoadingBox
import org.example.mp3player.presentation.common.TrackRow
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.action_back
import org.example.mp3player.presentation.resources.album_details_play_all
import org.example.mp3player.presentation.resources.album_tracks_count
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailsScreen(
    albumId: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailsViewModel = koinViewModel { parametersOf(albumId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AlbumDetailsEffect.OpenPlayer -> onOpenPlayer()
            }
        }
    }

    val title = (state as? AlbumDetailsUiState.Content)?.album?.title.orEmpty()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            AlbumDetailsUiState.Loading -> LoadingBox(Modifier.padding(padding))

            is AlbumDetailsUiState.Error -> ErrorBanner(
                message = current.errorText,
                onRetry = onBack,
                retryText = stringResource(Res.string.action_back),
                modifier = Modifier.padding(padding),
            )

            is AlbumDetailsUiState.Content -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                item {
                    AlbumHeader(
                        state = current,
                        onPlayAll = { viewModel.onEvent(AlbumDetailsEvent.PlayAll) },
                    )
                }
                itemsIndexed(
                    items = current.tracks,
                    key = { _, track -> track.id },
                ) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { viewModel.onEvent(AlbumDetailsEvent.PlayTrack(index)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    state: AlbumDetailsUiState.Content,
    onPlayAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            data = state.album.coverUri,
            contentDescription = state.album.title,
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(state.album.artist, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(Res.string.album_tracks_count, state.tracks.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onPlayAll, enabled = state.tracks.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(
                    text = stringResource(Res.string.album_details_play_all),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
```

**`koinViewModel { parametersOf(albumId) }` в значении по умолчанию.** `albumId` объявлен раньше в том же списке параметров, поэтому Kotlin разрешает на него ссылаться. Трейлинг-лямбда попадает в параметр `parameters` функции `koinViewModel(...)`, и Koin передаст её в лямбду `viewModel { (albumId: String) -> ... }` из `PresentationModule`.

**`@OptIn(ExperimentalMaterial3Api::class)`.** `TopAppBar` в Material 3 всё ещё помечена `@ExperimentalMaterial3Api`: её API может измениться в следующих версиях. Без аннотации — ошибка компиляции «This material API is experimental». Аннотация — это осознанная подпись «я знаю, что при апдейте библиотеки этот код может потребовать правки».

**`Icons.AutoMirrored.Filled.ArrowBack`, а не `Icons.Default.ArrowBack`.** `AutoMirrored`-версии автоматически отзеркаливаются в RTL-локалях (арабский, иврит): там «назад» это стрелка вправо. Обычный `Icons.Default.ArrowBack` deprecated именно по этой причине.

**`(state as? AlbumDetailsUiState.Content)?.album?.title.orEmpty()`.** Заголовок нужен снаружи `when` — в `topBar`. Пока грузим, заголовка нет, показываем пустую строку. Цепочка читается справа налево: safe cast → safe call → safe call → `null` превращается в `""`.

Обрати внимание: точка перед `orEmpty()` **без вопросительного знака**. `?.title` уже даёт `String?`, а `orEmpty()` — extension **на nullable receiver**, она сама умеет обрабатывать `null`.

**`item { }` перед `itemsIndexed`.** `LazyColumn` позволяет смешивать одиночные элементы и списки в одном скроллящемся контейнере. Шапка альбома уезжает вверх при скролле вместе с треками — так и должно быть. Если бы мы вынесли `AlbumHeader` в `Column` над `LazyColumn`, она бы залипла и съела высоту экрана.

**`onRetry = onBack` в ветке ошибки.** Альбом не найден — повторять нечего, единственное разумное действие «вернуться». Переиспользуем компонент, подменив текст кнопки. Это работает благодаря тому, что в Шаге 4 мы вынесли `retryText` в параметр.

### Шаг 9 — `PlayerScreen`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/player/PlayerScreen.kt
package org.example.mp3player.presentation.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.presentation.common.CoverArt
import org.example.mp3player.presentation.common.EmptyState
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.player_close
import org.example.mp3player.presentation.resources.player_next
import org.example.mp3player.presentation.resources.player_nothing
import org.example.mp3player.presentation.resources.player_pause
import org.example.mp3player.presentation.resources.player_play
import org.example.mp3player.presentation.resources.player_previous
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(Res.string.player_close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (!state.hasTrack) {
            EmptyState(
                title = stringResource(Res.string.player_nothing),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoverArt(
                data = state.coverUri,
                contentDescription = state.title,
                cornerRadius = 16.dp,
                modifier = Modifier.size(280.dp),
            )

            Spacer(Modifier.height(32.dp))
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(32.dp))
            Slider(
                value = state.progress,
                onValueChange = { viewModel.onEvent(PlayerEvent.SeekToFraction(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(state.positionText, style = MaterialTheme.typography.labelSmall)
                Text(state.durationText, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.onEvent(PlayerEvent.Previous) }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(Res.string.player_previous),
                    )
                }
                FilledIconButton(
                    onClick = { viewModel.onEvent(PlayerEvent.PlayPause) },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) Res.string.player_pause else Res.string.player_play
                        ),
                    )
                }
                IconButton(onClick = { viewModel.onEvent(PlayerEvent.Next) }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(Res.string.player_next),
                    )
                }
            }
        }
    }
}
```

**`return@Scaffold` — ранний выход из лямбды.** `Scaffold` принимает `content: @Composable (PaddingValues) -> Unit`; просто `return` вышел бы из `PlayerScreen`, а нам надо выйти только из content-лямбды. Метка `@Scaffold` (по имени функции) указывает, из какой именно лямбды выходим.

**`stringResource(if (...) A else B)`** — `Res.string.player_pause` это обычное значение типа `StringResource`, поэтому `if` как выражение работает штатно. Без такого приёма пришлось бы дублировать весь блок `Icon`.

**`contentDescription` у кнопок обязателен.** Это то, что прочитает TalkBack. У декоративных элементов (иконка внутри кнопки с текстом) — наоборот, `null`, чтобы скринридер не читал одно и то же дважды.

**Почему `state.progress`, а не `positionMs / durationMs` прямо здесь.** Пересчёт уже сделан в `PlayerViewModel.toUi()` и зажат в `0f..1f`. Композабл только рисует. Если бы делили здесь, при `durationMs == 0` получили бы `NaN` — и `Slider` упал бы.

### Шаг 10 — `UserAlbumsScreen`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/useralbums/UserAlbumsScreen.kt
package org.example.mp3player.presentation.useralbums

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.presentation.common.EmptyState
import org.example.mp3player.presentation.common.LoadingBox
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.album_tracks_count
import org.example.mp3player.presentation.resources.user_album_delete
import org.example.mp3player.presentation.resources.user_albums_create
import org.example.mp3player.presentation.resources.user_albums_empty_description
import org.example.mp3player.presentation.resources.user_albums_empty_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UserAlbumsScreen(
    onSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserAlbumsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UserAlbumsEffect.ShowMessage -> onSnackbar(effect.text)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.user_albums_create),
                )
            }
        },
    ) { padding ->
        when (val current = state) {
            UserAlbumsUiState.Loading -> LoadingBox(Modifier.padding(padding))

            UserAlbumsUiState.Empty -> EmptyState(
                title = stringResource(Res.string.user_albums_empty_title),
                description = stringResource(Res.string.user_albums_empty_description),
                modifier = Modifier.padding(padding),
            )

            is UserAlbumsUiState.Content -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                items(items = current.albums, key = { it.id }) { album ->
                    ListItem(
                        headlineContent = { Text(album.title) },
                        supportingContent = {
                            Text(stringResource(Res.string.album_tracks_count, album.trackIds.size))
                        },
                        trailingContent = {
                            IconButton(
                                onClick = { viewModel.onEvent(UserAlbumsEvent.Delete(album.id)) },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(Res.string.user_album_delete),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateUserAlbumDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title ->
                viewModel.onEvent(UserAlbumsEvent.Create(title))
                showCreateDialog = false
            },
        )
    }
}
```

**`var showCreateDialog by remember { mutableStateOf(false) }` — а как же «состояние только в ViewModel»?**

Правило из главы 06 звучало как «состояние экрана — в ViewModel». Здесь исключение, и оно осознанное: «открыт ли диалог» — это **чисто UI-состояние**. Оно не влияет на данные, не нужно другим слоям, и его потеря при повороте экрана максимум слегка раздражает.

Граница простая: если состояние переживает поворот экрана и влияет на данные — в ViewModel. Если это временное состояние виджета (открыт ли dropdown, развёрнута ли карточка, позиция скролла) — `remember` в Composable. Тащить каждое такое в `UiState` — раздувать sealed-иерархию до неузнаваемости.

(Если поворот всё-таки важен — `rememberSaveable` вместо `remember`: он кладёт значение в `Bundle`.)

**Диалог вызывается вне `Scaffold`.** `AlertDialog` в Compose рисуется в отдельном окне поверх всего, ему не нужны отступы Scaffold и он не должен попадать в content-лямбду. Стандартное место — в конце тела экрана, на одном уровне со `Scaffold`.

### Шаг 11 — `CreateUserAlbumDialog`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/useralbums/CreateUserAlbumDialog.kt
package org.example.mp3player.presentation.useralbums

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.user_album_dialog_cancel
import org.example.mp3player.presentation.resources.user_album_dialog_create
import org.example.mp3player.presentation.resources.user_album_dialog_name_hint
import org.example.mp3player.presentation.resources.user_album_dialog_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreateUserAlbumDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.user_album_dialog_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.user_album_dialog_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.trim()) },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(Res.string.user_album_dialog_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.user_album_dialog_cancel))
            }
        },
    )
}
```

`enabled = title.isNotBlank()` — UX-подсказка: кнопка серая, пока не ввели название. Это **не** замена валидации: настоящая проверка живёт в `UserAlbumsViewModel.create()` (глава 06, 4.3). UI может ошибиться или измениться, ViewModel — последний рубеж.

`onDismissRequest` вызывается при тапе мимо диалога и по системной кнопке «назад». Обязательный параметр — без него диалог невозможно было бы закрыть.

### Шаг 12 — Граф навигации

Все пять экранов написаны, можно их связать. `NavHost` — это `@Composable`, который держит граф и рендерит ту destination, что сейчас наверху back stack'а.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/navigation/AppNavHost.kt
package org.example.mp3player.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.example.mp3player.presentation.albumdetails.AlbumDetailsScreen
import org.example.mp3player.presentation.albums.AlbumsScreen
import org.example.mp3player.presentation.player.PlayerScreen
import org.example.mp3player.presentation.tracks.TracksScreen
import org.example.mp3player.presentation.useralbums.UserAlbumsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    onSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TracksRoute,
        modifier = modifier,
    ) {
        composable<TracksRoute> {
            TracksScreen(
                onOpenPlayer = { navController.navigate(PlayerRoute) },
                onSnackbar = onSnackbar,
            )
        }

        composable<AlbumsRoute> {
            AlbumsScreen(
                onAlbumClick = { albumId ->
                    navController.navigate(AlbumDetailsRoute(albumId))
                },
            )
        }

        composable<UserAlbumsRoute> {
            UserAlbumsScreen(
                onSnackbar = onSnackbar,
            )
        }

        composable<AlbumDetailsRoute> { backStackEntry ->
            val args: AlbumDetailsRoute = backStackEntry.toRoute()
            AlbumDetailsScreen(
                albumId = args.albumId,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate(PlayerRoute) },
            )
        }

        composable<PlayerRoute> {
            PlayerScreen(
                onClose = { navController.popBackStack() },
            )
        }
    }
}
```

**`composable<T>` — что даёт reified-тип.** Это inline-функция с reified-параметром:

```kotlin - иллюстрация (сигнатура из библиотеки, не писать)
inline fun <reified T : Any> NavGraphBuilder.composable(
    typeMap: Map<KType, NavType<*>> = emptyMap(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
)
```

`reified T` означает: компилятор знает реальный тип `T` в момент вызова и может использовать его в рантайме (через `T::class`, `serializer<T>()`). Под капотом `composable<AlbumDetailsRoute>` достаёт `serializer<AlbumDetailsRoute>()` (сгенерированный плагином) и регистрирует destination с ним.

`backStackEntry.toRoute<AlbumDetailsRoute>()` — тоже extension с reified. Достаёт сохранённую строку и декодирует в нужный тип. Тип выводится из объявления `val args: AlbumDetailsRoute`, поэтому явные угловые скобки не нужны.

Старое API было через строки: `entry.arguments?.getString("albumId")` — с опечатками, с ручным парсингом, без проверок компилятора.

`navigate(X)` кладёт X наверх back stack'а. `popBackStack()` снимает верхний элемент. Системная кнопка «назад» эквивалентна `popBackStack()`; если стек пуст — Activity закрывается.

**Каждый экран получает от графа только колбэки, а не `navController`.** `TracksScreen` не знает, что бывает какой-то `PlayerRoute` — он знает лишь «есть функция `onOpenPlayer`, зови её». Благодаря этому экран можно переиспользовать в другом графе, положить в `@Preview` и протестировать без навигации.

### Шаг 13 — `MainScaffold`: нижнее меню и снэкбар

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/root/MainScaffold.kt
package org.example.mp3player.presentation.root

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.example.mp3player.presentation.navigation.AlbumsRoute
import org.example.mp3player.presentation.navigation.AppNavHost
import org.example.mp3player.presentation.navigation.Route
import org.example.mp3player.presentation.navigation.TracksRoute
import org.example.mp3player.presentation.navigation.UserAlbumsRoute
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.tab_albums
import org.example.mp3player.presentation.resources.tab_my_albums
import org.example.mp3player.presentation.resources.tab_tracks
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class BottomItem(
    val route: Route,
    val icon: ImageVector,
    val labelRes: StringResource,
)

private val BottomItems = listOf(
    BottomItem(TracksRoute, Icons.Default.MusicNote, Res.string.tab_tracks),
    BottomItem(AlbumsRoute, Icons.Default.Album, Res.string.tab_albums),
    BottomItem(UserAlbumsRoute, Icons.Default.PlaylistPlay, Res.string.tab_my_albums),
)

@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentDestination.isTopLevel()) {
                NavigationBar {
                    BottomItems.forEach { item ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.hasRoute(item.route::class) } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(item.route) {
                                        popUpTo(TracksRoute) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AppNavHost(
            navController = navController,
            onSnackbar = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
            modifier = Modifier.padding(padding),
        )
    }
}

private fun NavDestination?.isTopLevel(): Boolean {
    if (this == null) return false
    return BottomItems.any { hasRoute(it.route::class) }
}
```

**Снэкбар: почему `rememberCoroutineScope`, а не `LaunchedEffect`.**

`snackbarHostState.showSnackbar(message)` — это **suspend**-функция: она приостанавливается на всё время показа снэкбара и возвращает `SnackbarResult` (было ли нажато действие). Значит, звать её надо из корутины.

Но `onSnackbar` — обычная лямбда `(String) -> Unit`, которую дёргает экран в ответ на эффект. `LaunchedEffect` тут не подходит: он запускается по ключу при композиции, а нам нужно запустить корутину **в момент события**.

`rememberCoroutineScope()` даёт ровно это: `CoroutineScope`, привязанный к точке композиции. Он отменяется, когда `MainScaffold` уходит из composition. Внутри лямбды-обработчика делаем `scope.launch { ... }` — стандартный приём «suspend-функция из не-suspend колбэка».

Так замкнулась цепочка эффектов: `TracksViewModel` эмитит `ShowMessage` → `TracksScreen` в `collect` зовёт `onSnackbar(text)` → `AppNavHost` пробрасывает его выше → `MainScaffold` показывает снэкбар. Ни один экран не знает, где физически живёт `SnackbarHostState`.

**`popUpTo + saveState + restoreState + launchSingleTop` — каждая опция отдельно.**

`popUpTo(TracksRoute)` — «перед добавлением новой destination удали из стека всё **до** `TracksRoute` (не включая её)». Без этого при переключении «Треки → Альбомы → Треки → Альбомы» накопился бы стек длиной 4, и кнопка «назад» четыре раза возвращала бы по вкладкам. С `popUpTo` стек всегда короткий.

`saveState = true` (внутри `popUpTo`) — «удаляя destination'ы, сохрани их state (включая ViewModel и позицию скролла)». Без флага удалённые destination'ы умирают вместе со state.

`restoreState = true` — «добавляя destination, восстанови её state, если он есть в кэше». Без флага `navigate` всегда создаёт свежий экземпляр: новая ViewModel, нулевой скролл.

Пара `saveState + restoreState` даёт привычный эффект: вернулся на вкладку — всё как было.

`launchSingleTop = true` — «если destination уже наверху стека, не создавай дубль». Защита от двойного тапа по вкладке.

**`hasRoute(item.route::class)` + `hierarchy`.** `currentDestination.hierarchy` — последовательность от текущего узла **вверх по графу** до корня. Она нужна для вложенных графов (`navigation { composable<...> }` внутри основного): у нас граф плоский, но код с `hierarchy` останется рабочим, когда граф вырастет. `hasRoute(...)` сравнивает по `KClass`, а не по строке — type-safe.

`item.route::class` — `KClass` конкретного объекта. Для `data object TracksRoute` это `TracksRoute::class`.

**`isTopLevel()` через `BottomItems.any { ... }`.** Раньше эту проверку писали как явное перечисление `hasRoute(TracksRoute::class) || hasRoute(AlbumsRoute::class) || ...`. Проблема: добавил вкладку в `BottomItems` — забыл добавить в `isTopLevel()` — нижнее меню пропадает именно на новой вкладке. Вывод проверки из того же списка убирает целый класс багов. Общее правило: **один источник правды**, даже для трёх строк.

Эффект: на `AlbumDetailsRoute` и `PlayerRoute` нижнее меню скрывается — экран плеера открывается на весь экран поверх вкладок.

**`Scaffold` — slot API.** Каждый параметр (`topBar`, `bottomBar`, `snackbarHost`, `floatingActionButton`, `content`) — это `@Composable () -> Unit`. Scaffold размещает их и считает `PaddingValues` с учётом размеров баров и system bars. **Всегда применяй `Modifier.padding(padding)`** к содержимому — иначе контент уедет под нижнее меню и под status bar.

Именно поэтому мы в Шаге 6 добавили `modifier` в `TracksScreen`: `MainScaffold` → `AppNavHost(modifier = Modifier.padding(padding))` → `NavHost` вешает его на свой контейнер → все экраны внутри получают правильные отступы автоматически.

### Шаг 14 — Подключаем к приложению

`RootScreen` из главы 06 показывал `TracksScreen` напрямую. Заменяем на полноценный `MainScaffold`:

```kotlin
// shared/src/commonMain/kotlin/org/example/mp3player/shared/RootScreen.kt
package org.example.mp3player.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.example.mp3player.presentation.root.MainScaffold

@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    MaterialTheme {
        MainScaffold()
    }
}
```

`MainActivity` менять не нужно — она с главы 06 (1.10) вызывает `RootScreen()` внутри `AudioPermissionGate`, а мы только что поменяли начинку самого `RootScreen`:

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/MainActivity.kt — напоминание, править нечего
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioPermissionGate {
                RootScreen()
            }
        }
    }
}
```

В этом и смысл слоёв: разрешение спрашивается в Android-точке входа, навигация живёт в `:shared:presentation`, и добавление всего графа навигации не потребовало ни строчки правок в `MainActivity`.

**`enableEdgeToEdge()` (из `androidx.activity`)** делает status bar и navigation bar **прозрачными** и позволяет контенту рисоваться под ними. Современный Material 3 дизайн именно такой — нет «полосок» по краям.

Подвох: контент действительно лезет под бары, и без учёта `WindowInsets` текст уедет под status bar. Решение уже работает: `Scaffold` по умолчанию использует `contentWindowInsets = ScaffoldDefaults.contentWindowInsets`, который включает system bars, и его `padding` учитывает их высоту. Если нужен ручной контроль — `Modifier.windowInsetsPadding(WindowInsets.statusBars)`.

`MaterialTheme { ... }` оборачивает контент в палитру/типографику/формы Material 3. Без него цвета будут дефолтными.

### Шаг 15 — Добавить трек в «мой альбом»

Осталась одна незамкнутая цепочка. В главе 06 у `TracksViewModel` появилось событие `TracksEvent.AddToUserAlbum`, обработчик `addToAlbum()` и зависимость `userAlbumsRepository` — но **никто это событие не шлёт**. У `TrackRow` (06, 1.7) есть слот `trailingContent`, который никто не использует. В итоге «Мои альбомы» умеют только создаваться и удаляться — положить туда трек нечем.

Замыкаем: справа в строке трека — иконка «плюс», по тапу диалог со списком твоих альбомов, выбор → трек добавлен.

**15.1 — ViewModel отдаёт список альбомов.** Диалог должен что-то показать, а `TracksUiState` про пользовательские альбомы не знает. Добавлять их в `Content` не будем: они не влияют на отрисовку списка треков, и лишнее поле в `Content` заставляло бы пересобирать состояние экрана при каждом переименовании альбома. Заводим **отдельный** `StateFlow` рядом.

Открываем `TracksViewModel.kt` и добавляем одно свойство (всё остальное не трогаем):

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/tracks/TracksViewModel.kt
// добавляем к тому, что уже написано в главе 06 (1.4)

// новый импорт всего один — остальное (StateFlow, SharingStarted, stateIn) уже есть с главы 06:
// import org.example.mp3player.domain.model.UserAlbum

    /** Список пользовательских альбомов — нужен только диалогу «добавить в альбом». */
    val userAlbums: StateFlow<List<UserAlbum>> = userAlbumsRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
```

Ничего нового: тот же `stateIn`, что и у `state`. `userAlbumsRepository` уже в конструкторе с главы 06 — новых зависимостей не появилось, правка `PresentationModule` не нужна.

**Почему второй `StateFlow`, а не поле в `Content`.** Это общее правило: **в `UiState` кладут то, от чего зависит картинка экрана.** Список альбомов виден только внутри диалога и только пока он открыт. С отдельным потоком `WhileSubscribed` сам решит не держать подписку, пока диалог закрыт и его никто не читает.

**15.2 — Диалог выбора альбома.**

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/useralbums/AddToUserAlbumDialog.kt
package org.example.mp3player.presentation.useralbums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.mp3player.domain.model.UserAlbum
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.add_to_album_empty
import org.example.mp3player.presentation.resources.add_to_album_title
import org.example.mp3player.presentation.resources.user_album_dialog_cancel
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddToUserAlbumDialog(
    albums: List<UserAlbum>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_to_album_title)) },
        text = {
            if (albums.isEmpty()) {
                Text(stringResource(Res.string.add_to_album_empty))
            } else {
                LazyColumn {
                    items(items = albums, key = { it.id }) { album ->
                        ListItem(
                            headlineContent = { Text(album.title) },
                            modifier = Modifier.clickable { onPick(album.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                Text(stringResource(Res.string.user_album_dialog_cancel))
            }
        },
    )
}
```

`items(items = albums, key = { it.id })` — обычный `items` (не `itemsIndexed`): индекс не нужен, наружу уходит `album.id`.

`confirmButton` с текстом «Отмена» и без `dismissButton` — у диалога нет действия «ОК»: выбор происходит тапом по строке, а кнопка нужна одна, чтобы закрыть. `AlertDialog` требует `confirmButton` обязательным параметром, поэтому единственную кнопку кладём туда.

`LazyColumn` внутри `AlertDialog` работает: диалог задаёт своему `text`-слоту максимальную высоту, а `LazyColumn` умеет скроллиться в ограниченной высоте. Ср. с подводным камнем №8 — там `LazyVerticalGrid` внутри `Column`, у которого высота **не** ограничена, и это падает.

**15.3 — Кнопка в строке трека.** Правим `TracksContent` в `TracksScreen.kt` — только `LazyColumn` и то, что вокруг него. Полное тело функции после правки:

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/tracks/TracksScreen.kt
// добавочные импорты:
// import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.filled.Add
// import androidx.compose.material3.Icon
// import androidx.compose.material3.IconButton
// import androidx.compose.runtime.mutableStateOf
// import androidx.compose.runtime.remember
// import androidx.compose.runtime.setValue
// import org.example.mp3player.domain.model.UserAlbum
// import org.example.mp3player.presentation.resources.add_to_album
// import org.example.mp3player.presentation.useralbums.AddToUserAlbumDialog

@Composable
private fun TracksContent(
    state: TracksUiState.Content,
    userAlbums: List<UserAlbum>,
    onEvent: (TracksEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // id трека, который сейчас добавляем; null — диалог закрыт.
    var pendingTrackId by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onEvent(TracksEvent.Search(it)) },
            placeholder = { Text(stringResource(Res.string.tracks_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        val tracks = state.filteredTracks
        when {
            state.tracks.isEmpty() -> EmptyState(
                title = stringResource(Res.string.tracks_empty_title),
                description = stringResource(Res.string.tracks_empty_description),
            )

            tracks.isEmpty() -> EmptyState(
                title = stringResource(Res.string.tracks_no_results),
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.id },
                ) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onEvent(TracksEvent.PlayTrack(index)) },
                        trailingContent = {
                            IconButton(onClick = { pendingTrackId = track.id }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(Res.string.add_to_album),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    val trackId = pendingTrackId
    if (trackId != null) {
        AddToUserAlbumDialog(
            albums = userAlbums,
            onDismiss = { pendingTrackId = null },
            onPick = { albumId ->
                onEvent(TracksEvent.AddToUserAlbum(trackId = trackId, albumId = albumId))
                pendingTrackId = null
            },
        )
    }
}
```

И в `TracksScreen` — подписка на новый поток и передача его вниз:

```kotlin
@Composable
fun TracksScreen(
    onOpenPlayer: () -> Unit,
    onSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TracksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val userAlbums by viewModel.userAlbums.collectAsStateWithLifecycle()   // ← новое

    // ... оба LaunchedEffect без изменений ...

    when (val current = state) {
        TracksUiState.Loading -> LoadingBox(modifier)

        is TracksUiState.Error -> ErrorBanner(
            message = current.errorText,
            onRetry = { viewModel.onEvent(TracksEvent.Refresh) },
            modifier = modifier,
        )

        is TracksUiState.Content -> TracksContent(
            state = current,
            userAlbums = userAlbums,                                       // ← новое
            onEvent = viewModel::onEvent,
            modifier = modifier,
        )
    }
}
```

**`var pendingTrackId by remember { mutableStateOf<String?>(null) }` — состояние диалога в Composable, а не в ViewModel.** Та же логика, что и с `showCreateDialog` в Шаге 10: «какой трек сейчас добавляем» — чисто UI-состояние, оно не переживает переход на другой экран и никому больше не нужно. Один nullable-`String` заменяет пару «`showDialog: Boolean` + `selectedTrackId: String?`» и делает невозможным состояние «диалог открыт, а трек не выбран».

**`val trackId = pendingTrackId` перед `if`.** Тот же приём, что `when (val current = state)`: копируем в локальный `val`, чтобы внутри `if` компилятор сделал smart cast `String? → String` и `trackId` можно было передать в лямбду без `!!`. С делегированным `by`-свойством напрямую это не сработает.

**Диалог объявлен вне `Column`, в конце тела функции.** `AlertDialog` рисуется в собственном окне поверх всего; класть его внутрь скроллящейся колонки не нужно и вредно.

**Про текст «Добавлено».** `TracksViewModel.addToAlbum()` эмитит `TracksEffect.ShowMessage("Добавлено")` — строка захардкожена в ViewModel, а ресурсы (`Res.string.*`) доступны только из `@Composable`. Для учебного проекта это допустимо. Правильное решение, если захочешь довести локализацию до конца: эффект несёт не текст, а **тип сообщения** (`data object AddedToAlbum : TracksEffect`), а экран уже переводит его в строку через `stringResource`. Ресурс `add_to_album_done` мы завели в Шаге 2 как раз для такого варианта.

Проверь: вкладка «Мои альбомы» → создай альбом → вкладка «Треки» → «плюс» напротив трека → выбери альбом → снэкбар «Добавлено». Если альбомов нет, диалог честно скажет, что сначала надо создать альбом.

---

## Чек-лист: что должно быть к концу главы

Написано и компилируется:

- [ ] `composeResources/values/strings.xml` и `values-en/strings.xml`
- [ ] `navigation/Route.kt` — пять маршрутов
- [ ] `common/CoverArt.kt`; `common/StateViews.kt` обновлён
- [ ] `tracks/TracksScreen.kt` обновлён (строки + `modifier`)
- [ ] `albums/AlbumsScreen.kt`, `albumdetails/AlbumDetailsScreen.kt`, `player/PlayerScreen.kt`
- [ ] `useralbums/UserAlbumsScreen.kt`, `useralbums/CreateUserAlbumDialog.kt`
- [ ] `useralbums/AddToUserAlbumDialog.kt`; `TracksViewModel` отдаёт `userAlbums`
- [ ] `navigation/AppNavHost.kt`, `root/MainScaffold.kt`
- [ ] `shared/RootScreen.kt` показывает `MainScaffold`

Работает при запуске:

- [ ] Три вкладки внизу, переключаются, скролл сохраняется при возврате.
- [ ] Тап по треку → открывается плеер, нижнее меню скрыто.
- [ ] Тап по альбому → детали, кнопка «назад» в тулбаре и системная работают.
- [ ] «Мои альбомы» → FAB → диалог → альбом появился в списке; иконка корзины удаляет.
- [ ] «Плюс» напротив трека → выбор альбома → снэкбар «Добавлено».
- [ ] Смена языка системы на English меняет весь интерфейс.

---

## Подводные камни

### 1. `@Serializable` без плагина
Забыл `alias(libs.plugins.kotlinx.serialization)` в `build.gradle.kts` → при навигации `SerializationException: Serializer for class 'AlbumDetailsRoute' is not found`. Плагин обязателен, и версии плагина и runtime-библиотеки — разные (см. Шаг 1).

### 2. Route объявлен как `class`, а не `data class` / `data object`
Без `data` — дефолтные `equals`/`hashCode` по ссылке → навигация ведёт себя странно, «текущая destination» не совпадает. Всегда `data class` / `data object`.

### 3. Разные типы Route в `composable<X>` и `navigate(Y)`
Навигируешь `navigate(AlbumDetailsRoute("123"))`, а зарегистрирован только `PlayerRoute` — `IllegalArgumentException` в рантайме: «Navigation destination that matches request ... cannot be found». Каждый `navigate` должен иметь парный `composable<>`.

### 4. `LaunchedEffect(currentBackStackEntry)`
`currentBackStackEntryAsState()` — это `State`, меняющийся при каждой навигации. Использовать его для вычисления `selected` у вкладки — правильно; повесить на него `LaunchedEffect`, который навигирует, — гарантированный бесконечный цикл.

### 5. Compose Resources не генерируются
Забыл блок `compose.resources { publicResClass = true }` → `Res.string.*` не виден из другого модуля. Либо пакет не пересобран — нужна полная пересборка (`Build → Clean Project`, потом `Rebuild`).

### 6. Две разные `NavController` в одном приложении
Если в `MainScaffold` создал `rememberNavController()`, а внутри экрана — ещё один, твой `navigate` пойдёт не туда, куда думаешь. Один контроллер, колбэки вниз.

### 7. Забыл `Modifier.padding(padding)` от `Scaffold`
Контент уезжает под нижнее меню и под системные бары. Симптом: последний элемент списка не доскроллить.

### 8. `LazyVerticalGrid` внутри `Column` без ограничения высоты
`IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`. Либо `Modifier.weight(1f)` в `Column`, либо родитель с фиксированной высотой.

### 9. `TopAppBar` без `@OptIn(ExperimentalMaterial3Api::class)`
Ошибка компиляции «This material API is experimental and is likely to change». Аннотация ставится над функцией, где используется.

### 10. `Icons.Default.ArrowBack` вместо `Icons.AutoMirrored.Filled.ArrowBack`
Компилируется с deprecation-warning, но в RTL-локалях стрелка смотрит не туда.

---

## Try yourself

1. **Правильные склонения**: замени `album_tracks_count` на plurals. В Compose Resources это `<plurals name="tracks_count">` с `<item quantity="one|few|many|other">` и `pluralStringResource(Res.plurals.tracks_count, count, count)`.

2. **`UserAlbumDetailsScreen`**: добавь маршрут `@Serializable data class UserAlbumDetailsRoute(val albumId: Long)`, ViewModel по образцу `AlbumDetailsViewModel` (там уже есть паттерн с `parametersOf`), экран со списком треков альбома. Понадобится связать `UserAlbum.trackIds` с `TracksRepository.observeTracks()` — хороший повод потренировать `combine`.

3. **Подтверждение удаления**: сейчас корзина удаляет альбом сразу. Добавь `AlertDialog` «Удалить альбом?» — по образцу `CreateUserAlbumDialog`.

4. **`BackHandler` в плеере**: проверь, что системная «назад» на экране плеера закрывает экран, но **не** останавливает воспроизведение. Если поведение не такое — разберись почему.

5. **Мини-плеер**: полоска над нижним меню с текущим треком и кнопкой play/pause, по тапу открывает `PlayerScreen`. Подсказка: она живёт в `MainScaffold`, `PlayerViewModel` можно получить там же через `koinViewModel()`.

6. **Deep link на альбом**: добавь в `composable<AlbumDetailsRoute>` параметр `deepLinks = listOf(navDeepLink<AlbumDetailsRoute>(basePath = "app://album"))`. Проверь через `adb shell am start -a android.intent.action.VIEW -d "app://album/123"`.

7. **Тёмная тема**: `MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme())` в `RootScreen`. Проверь, что плейсхолдер обложки не стал белым пятном.

---

## Дальше

→ [`08-COVER_ART.md`](./08-COVER_ART.md)

## Ссылки

- [Navigation Compose — Type-safe navigation](https://developer.android.com/guide/navigation/design/type-safety)
- [Navigation Compose KMP](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-navigation.html)
- [Compose Resources](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-resources.html)
- [Material 3 NavigationBar](https://developer.android.com/develop/ui/compose/components/navigation-bar)
- [Snackbar в Compose](https://developer.android.com/develop/ui/compose/components/snackbar)
