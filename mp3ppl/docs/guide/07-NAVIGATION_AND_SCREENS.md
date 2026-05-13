# 07. Навигация и экраны + локализация

## Зачем

К этому моменту у нас есть отдельные экраны (`TracksScreen`, `PlayerScreen`, …), но нет способа переключаться между ними. Нужна навигация.

Требования:
- **Back stack** — системная кнопка "назад" возвращает на предыдущий экран.
- **Передача данных** — на `AlbumDetailsScreen` надо как-то передать `albumId`.
- **Type safety** — компилятор должен проверить, что передаём правильные аргументы.
- **Не терять state** при навигации туда-обратно.
- **Deep links** (на будущее).

**Navigation Compose** (AndroidX) — стандарт для Compose-приложений. В KMP есть совместимая версия `org.jetbrains.androidx.navigation:navigation-compose`. Начиная с 2.8 поддерживает **type-safe routes** через `@Serializable` data class — именно их мы и используем.

Плюс: Material 3 `Scaffold` + `NavigationBar` дадут нам нижнее меню в 20 строк.

Плюс: локализация через **Compose Resources** — аналог `strings.xml`, но кросс-платформенный.

---

## Что реализуем

1. Подключение Navigation Compose (KMP-версия) и Compose Resources.
2. Файл-граф со всеми маршрутами.
3. `MainScaffold` с нижней навигацией (Tracks, Albums, MyAlbums) и вложенный NavHost.
4. Отдельный `PlayerScreen` как fullscreen-экран поверх нижней навигации.
5. Type-safe передача `albumId` в `AlbumDetailsScreen`.
6. Локализация RU/EN через Compose Resources.
7. Четыре реальных экрана с примерами UI.

Новые файлы:

```
shared/presentation/src/commonMain/
├── composeResources/
│   ├── values/strings.xml                     (ru — default)
│   └── values-en/strings.xml                  (en)
└── kotlin/org/example/mp3player/presentation/
    ├── navigation/
    │   ├── Routes.kt                          (все sealed-маршруты)
    │   └── AppNavHost.kt                      (граф)
    ├── root/
    │   └── MainScaffold.kt                    (нижняя навигация + NavHost)
    ├── tracks/TracksScreen.kt                 (обновляем из 06)
    ├── albums/AlbumsScreen.kt                 (новый, Grid)
    ├── albumdetails/AlbumDetailsScreen.kt     (новый)
    ├── player/PlayerScreen.kt                 (новый)
    ├── useralbums/
    │   ├── UserAlbumsScreen.kt                (новый)
    │   └── CreateUserAlbumDialog.kt           (новый)
    └── common/
        ├── TrackRow.kt                        (переиспользуемый)
        └── EmptyState.kt                      (переиспользуемый)
```

---

## Реализация

### Шаг 1 — Зависимости

`gradle/libs.versions.toml`:

```toml
[versions]
navigationCompose = "2.9.0-alpha11"   # версия для KMP, проверь актуальную
kotlinxSerialization = "1.9.0"

[libraries]
androidx-navigation-compose = { group = "org.jetbrains.androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

[plugins]
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`shared/presentation/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlinSerialization)
    // ... остальное
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "org.example.mp3player.presentation.resources"
}
```

### Шаг 2 — Строки

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

    <string name="user_albums_empty_title">Создай свой первый альбом</string>
    <string name="user_albums_empty_description">Добавляй треки из библиотеки</string>
    <string name="user_albums_create">Создать альбом</string>
    <string name="user_album_dialog_title">Новый альбом</string>
    <string name="user_album_dialog_name_hint">Название</string>
    <string name="user_album_dialog_create">Создать</string>
    <string name="user_album_dialog_cancel">Отмена</string>

    <string name="player_previous">Предыдущий</string>
    <string name="player_next">Следующий</string>
    <string name="player_play">Играть</string>
    <string name="player_pause">Пауза</string>

    <string name="action_refresh">Обновить</string>
    <string name="action_retry">Попробовать ещё раз</string>
    <string name="action_add_to_album">Добавить в альбом</string>
    <string name="action_remove_from_album">Убрать из альбома</string>

    <string name="error_loading">Не удалось загрузить</string>
    <string name="permission_needed_title">Нужен доступ к музыке</string>
    <string name="permission_needed_description">Без разрешения приложение не видит ваши треки</string>
    <string name="permission_grant">Разрешить</string>
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

    <string name="user_albums_empty_title">Create your first album</string>
    <string name="user_albums_empty_description">Add tracks from your library</string>
    <string name="user_albums_create">Create album</string>
    <string name="user_album_dialog_title">New album</string>
    <string name="user_album_dialog_name_hint">Title</string>
    <string name="user_album_dialog_create">Create</string>
    <string name="user_album_dialog_cancel">Cancel</string>

    <string name="player_previous">Previous</string>
    <string name="player_next">Next</string>
    <string name="player_play">Play</string>
    <string name="player_pause">Pause</string>

    <string name="action_refresh">Refresh</string>
    <string name="action_retry">Try again</string>
    <string name="action_add_to_album">Add to album</string>
    <string name="action_remove_from_album">Remove from album</string>

    <string name="error_loading">Failed to load</string>
    <string name="permission_needed_title">Music access needed</string>
    <string name="permission_needed_description">Without permission the app cannot see your tracks</string>
    <string name="permission_grant">Grant</string>
</resources>
```

Использование:
```kotlin
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.*
import org.jetbrains.compose.resources.stringResource

Text(stringResource(Res.string.tracks_search_hint))
```

Сборка сгенерирует `Res.string.tracks_search_hint` автоматически. Язык выбирается системой (локаль устройства) — ничего настраивать в коде не надо.

### Шаг 3 — Routes

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/navigation/Routes.kt
package org.example.mp3player.presentation.navigation

import kotlinx.serialization.Serializable

/** Корневые вкладки bottom navigation. */
sealed interface TopLevelRoute {
    @Serializable data object Tracks : TopLevelRoute
    @Serializable data object Albums : TopLevelRoute
    @Serializable data object UserAlbums : TopLevelRoute
}

/** Экраны, которые живут поверх табов. */
@Serializable data class AlbumDetails(val albumId: String) : Route
@Serializable data class UserAlbumDetails(val albumId: Long) : Route
@Serializable data object Player : Route

sealed interface Route

// Для вкладок тоже пометим Route для удобства.
val TopLevelRoute.asRoute: Route
    get() = when (this) {
        TopLevelRoute.Tracks -> TracksRoute
        TopLevelRoute.Albums -> AlbumsRoute
        TopLevelRoute.UserAlbums -> UserAlbumsRoute
    }

@Serializable data object TracksRoute : Route
@Serializable data object AlbumsRoute : Route
@Serializable data object UserAlbumsRoute : Route
```

### Шаг 4 — NavHost

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/navigation/AppNavHost.kt
package org.example.mp3player.presentation.navigation

import androidx.compose.runtime.Composable
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
) {
    NavHost(
        navController = navController,
        startDestination = TracksRoute,
    ) {
        composable<TracksRoute> {
            TracksScreen(
                onOpenPlayer = { navController.navigate(Player) },
                onSnackbar = onSnackbar,
            )
        }
        composable<AlbumsRoute> {
            AlbumsScreen(
                onAlbumClick = { albumId ->
                    navController.navigate(AlbumDetails(albumId))
                },
            )
        }
        composable<UserAlbumsRoute> {
            UserAlbumsScreen(
                onAlbumClick = { id ->
                    navController.navigate(UserAlbumDetails(id))
                },
            )
        }
        composable<AlbumDetails> { backStackEntry ->
            val args: AlbumDetails = backStackEntry.toRoute()
            AlbumDetailsScreen(
                albumId = args.albumId,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate(Player) },
            )
        }
        composable<Player> {
            PlayerScreen(
                onClose = { navController.popBackStack() },
            )
        }
    }
}
```

### Шаг 5 — Главный экран с нижней навигацией

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/root/MainScaffold.kt
package org.example.mp3player.presentation.root

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.example.mp3player.presentation.navigation.*
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.tab_albums
import org.example.mp3player.presentation.resources.tab_my_albums
import org.example.mp3player.presentation.resources.tab_tracks
import org.jetbrains.compose.resources.stringResource

@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentDestination.isTopLevel()) {
                NavigationBar {
                    BottomItems.forEach { item ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.hasRoute(item.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) navController.navigate(item.route) {
                                    popUpTo(TracksRoute) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AppNavHost(
                navController = navController,
                onSnackbar = { msg ->
                    // Показ снэкбара как эффект
                    // Для простоты — через LaunchedEffect в каком-то месте.
                }
            )
        }
    }
}

private data class BottomItem(
    val route: Route,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelRes: org.jetbrains.compose.resources.StringResource,
)

private val BottomItems = listOf(
    BottomItem(TracksRoute, Icons.Default.MusicNote, Res.string.tab_tracks),
    BottomItem(AlbumsRoute, Icons.Default.Album, Res.string.tab_albums),
    BottomItem(UserAlbumsRoute, Icons.Default.PlaylistPlay, Res.string.tab_my_albums),
)

private fun androidx.navigation.NavDestination?.isTopLevel(): Boolean {
    if (this == null) return false
    return hasRoute(TracksRoute::class) || hasRoute(AlbumsRoute::class) || hasRoute(UserAlbumsRoute::class)
}
```

**Что делает `popUpTo(TracksRoute) { saveState = true }` + `restoreState = true`:**
Когда ты переключаешься между табами, НЕ пересоздаёт экран каждый раз — сохраняет `state` (ViewModel state, scroll position) и восстанавливает при возврате. Это то, что хочет пользователь: ушёл со вкладки Albums, вернулся — тот же скролл.

### Шаг 6 — `AlbumsScreen` (Grid)

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/albums/AlbumsScreen.kt
package org.example.mp3player.presentation.albums

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.example.mp3player.domain.Album
import org.example.mp3player.presentation.common.EmptyState
import org.example.mp3player.presentation.resources.Res
import org.example.mp3player.presentation.resources.albums_empty
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlbumsScreen(
    onAlbumClick: (String) -> Unit,
    viewModel: AlbumsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.albums.isEmpty()) {
        EmptyState(text = stringResource(Res.string.albums_empty))
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = state.albums, key = { it.id }) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album.id) })
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column {
            AsyncImage(
                model = album.coverUri,
                contentDescription = album.title,
                modifier = Modifier.aspectRatio(1f).fillMaxWidth(),
            )
            Column(Modifier.padding(12.dp)) {
                Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = "${album.trackCount} • ${album.totalDurationMs / 60000} мин",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
```

### Шаг 7 — `PlayerScreen`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/player/PlayerScreen.kt
package org.example.mp3player.presentation.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.example.mp3player.presentation.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = state.coverUri,
                contentDescription = null,
                modifier = Modifier.size(280.dp),
            )
            Spacer(Modifier.height(32.dp))
            Text(state.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(
                state.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Icons.Default.SkipPrevious,
                        contentDescription = stringResource(Res.string.player_previous),
                    )
                }
                FilledIconButton(
                    onClick = { viewModel.onEvent(PlayerEvent.PlayPause) },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) Res.string.player_pause else Res.string.player_play
                        ),
                    )
                }
                IconButton(onClick = { viewModel.onEvent(PlayerEvent.Next) }) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(Res.string.player_next),
                    )
                }
            }
        }
    }
}
```

### Шаг 8 — `UserAlbumsScreen` с диалогом создания

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/useralbums/UserAlbumsScreen.kt
package org.example.mp3player.presentation.useralbums

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.mp3player.presentation.common.EmptyState
import org.example.mp3player.presentation.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UserAlbumsScreen(
    onAlbumClick: (Long) -> Unit,
    viewModel: UserAlbumsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.user_albums_create))
            }
        }
    ) { padding ->
        if (state.albums.isEmpty()) {
            EmptyState(
                title = stringResource(Res.string.user_albums_empty_title),
                description = stringResource(Res.string.user_albums_empty_description),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(items = state.albums, key = { it.id }) { album ->
                    ListItem(
                        headlineContent = { Text(album.title) },
                        supportingContent = { Text("${album.trackIds.size} треков") },
                        modifier = Modifier.clickable { onAlbumClick(album.id) },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateUserAlbumDialog(
            onDismiss = { showCreate = false },
            onCreate = { title ->
                viewModel.onEvent(UserAlbumsEvent.Create(title))
                showCreate = false
            },
        )
    }
}
```

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/useralbums/CreateUserAlbumDialog.kt
package org.example.mp3player.presentation.useralbums

import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.example.mp3player.presentation.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreateUserAlbumDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
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

### Шаг 9 — MainActivity

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/MainActivity.kt
package org.example.mp3player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import org.example.mp3player.presentation.root.MainScaffold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                MainScaffold()
            }
        }
    }
}
```

---

## Разбор

### `@Serializable` — что плагин делает в момент сборки

```kotlin
@Serializable data class AlbumDetails(val albumId: String) : Route
```

`@Serializable` — аннотация из `kotlinx.serialization`. Сама по себе она ничего не делает — её обрабатывает **компилятор-плагин** `kotlinx-serialization`, подключаемый через `alias(libs.plugins.kotlinSerialization)`.

Что плагин генерирует на этапе сборки:

1. Для каждого `@Serializable`-класса создаётся **companion object с методом `serializer()`**, который возвращает `KSerializer<T>` — объект, умеющий кодировать/декодировать инстанс класса в structured-формат.
2. Этот `KSerializer` — универсальный: его можно использовать с JSON (`Json.encodeToString`), с binary (`Cbor.encodeToByteArray`), с собственными форматами.
3. Без плагина: `data class` помечен `@Serializable`, но `serializer()` не сгенерирован → `SerializationException: Serializer for class 'AlbumDetails' is not found`.

Navigation Compose 2.8+ использует этот `KSerializer` для **type-safe routes**:
- `navController.navigate(AlbumDetails("123"))` → `Json.encodeToString(serializer, value)` → URL-encoded string → кладётся в back stack.
- `entry.toRoute<AlbumDetails>()` → достаёт строку из back stack → `Json.decodeFromString(serializer, string)` → возвращает `AlbumDetails`.

В Logcat можешь увидеть фактический URL — что-то вроде `org.example.../AlbumDetails/123`. Это и есть закодированный data class.

### `composable<AlbumDetails> { entry -> entry.toRoute() }` — что reified-тип даёт

```kotlin
composable<AlbumDetails> { backStackEntry ->
    val args: AlbumDetails = backStackEntry.toRoute()
    AlbumDetailsScreen(albumId = args.albumId, ...)
}
```

`composable<T>` — это inline-функция с reified-параметром:

```kotlin
inline fun <reified T : Any> NavGraphBuilder.composable(
    typeMap: Map<KType, NavType<*>> = emptyMap(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
)
```

`reified T` означает: компилятор знает реальный тип `T` в момент вызова и может его использовать в рантайме (через `T::class`, `serializer<T>()`). Под капотом `composable<AlbumDetails>` достаёт `serializer<AlbumDetails>()` и регистрирует destination с этим serializer'ом.

`backStackEntry.toRoute<AlbumDetails>()` — extension-функция, тоже с reified. Она достаёт сохранённую строку и декодирует в нужный тип.

Старое API (string routes):

```kotlin
composable(route = "album_details/{albumId}") { entry ->
    val albumId = entry.arguments?.getString("albumId") ?: return@composable
    AlbumDetailsScreen(albumId)
}
```

Проблемы старого API:
- Имя параметра — строка, легко опечататься (`"albumid"` vs `"albumId"`).
- Тип параметра — нужно вручную доставать через `getString`/`getInt`.
- Рефакторинг IDE не работает (имена в строках).

Type-safe вариант решает всё это, но требует подключения сериализации.

### `navigate()` vs `popBackStack()` vs back stack

«Back stack» — это **список destination-ов** в порядке «куда возвращаться». Когда пользователь жмёт системный «назад», убирается верхний элемент стека и показывается тот, что под ним.

| Операция | Что делает с back stack |
|---|---|
| `navController.navigate(X)` | Кладёт X наверх стека |
| `navController.popBackStack()` | Снимает верхний элемент, возвращается к предыдущему |
| `navController.popBackStack(X, inclusive = false)` | Снимает всё **до** X (X остаётся наверху) |
| `navController.popBackStack(X, inclusive = true)` | Снимает всё **включая** X |

Системная кнопка «назад» эквивалентна `popBackStack()`. Если стек пуст — Activity закрывается.

### `popUpTo + saveState + restoreState + launchSingleTop` — каждая опция отдельно

```kotlin
navController.navigate(item.route) {
    popUpTo(TracksRoute) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

Это блок `NavOptionsBuilder`, в котором можно настроить, как именно происходит навигация. Разберём по флагу.

#### `popUpTo(TracksRoute)`

«Перед тем как добавить новую destination, удали из стека всё **до** `TracksRoute` (не включая её)».

Зачем: при переключении вкладок не накапливать стек. Без `popUpTo` сценарий «Tracks → Albums → Tracks → Albums → ...» дал бы стек длиной 4, и системный «назад» проходил бы по всем подряд. С `popUpTo(TracksRoute)` стек всегда краткий: либо просто Tracks, либо Tracks + текущая вкладка.

#### `saveState = true` (внутри popUpTo)

«Когда удаляешь destination'ы — сохрани их state (включая ViewModel и scroll position)».

Без флага: removed destination просто умирают, state теряется. С флагом: state кладётся в специальный кэш под ключом этой destination.

#### `restoreState = true`

«Когда добавляешь новую destination — если у неё в кэше есть сохранённый state, восстанови».

Без флага: navigate всегда создаёт свежий экземпляр destination (новая ViewModel, нулевой scroll). С флагом: если у этой destination когда-то был сохранён state (через `saveState`), он подтянется.

В паре `saveState + restoreState` дают эффект: «при переключении вкладок сохраняется ViewModel и scroll, при возврате на вкладку всё как было».

#### `launchSingleTop = true`

«Если destination, в которую навигируем, **уже** наверху стека — не создавай дубль, переиспользуй».

Зачем: если пользователь тапнул по уже выбранной вкладке (он уже на Tracks, тапнул Tracks ещё раз) — без флага создастся новый экземпляр Tracks поверх старого. Это дубль в стеке и потеря state. С флагом — операция превращается в no-op.

### `hasRoute(item.route::class)` + `hierarchy`

```kotlin
val selected = currentDestination?.hierarchy
    ?.any { it.hasRoute(item.route::class) } == true
```

`currentDestination` — это `NavDestination?`, узел в графе навигации. У узла есть свойство `hierarchy: Sequence<NavDestination>` — путь от текущего узла **вверх по графу** до корня.

Зачем последовательность: для **nested graphs** (когда у тебя `navigation { composable<...>; composable<...> }` внутри основного графа). Текущая destination — это лист, а её родительская навигационная группа — узел над ней. Чтобы определить «активна ли вкладка X», нужно проверить не только саму destination, но и все её родительские группы (вдруг X — это subgraph).

`hasRoute(item.route::class)` — extension, проверяющая, что destination зарегистрирована под маршрутом этого типа. Сравнивает по `KClass`, не по строке — type-safe.

В нашем плоском графе `hierarchy` всегда содержит 1-2 элемента (destination + root graph). Для сложных графов — больше.

### `Scaffold` — slot API и почему это удобно

```kotlin
Scaffold(
    snackbarHost = { SnackbarHost(snackbar) },
    bottomBar = { ... },
    floatingActionButton = { ... },
    topBar = { ... },
) { padding -> Box(Modifier.padding(padding)) { ... } }
```

`Scaffold` — реализация Material 3-структуры экрана. Под капотом это `Layout`, который размещает свои «слоты» (top bar, bottom bar, FAB, content) и считает padding для контента так, чтобы он не уезжал под them.

«Slot API» означает: каждый параметр — это `@Composable () -> Unit` (или с параметрами). Ты пишешь, что положить в слот, а Scaffold сам решает где и как разместить.

#### `padding: PaddingValues` в content-лямбде

Scaffold вычисляет, сколько места занимают top bar / bottom bar / system bars (status bar, navigation bar), и собирает это в `PaddingValues` — структура с четырьмя сторонами (top, bottom, start, end). Дальше эта структура передаётся в content-лямбду как параметр.

```kotlin
{ padding -> Box(Modifier.padding(padding)) { ... } }
```

Если **не применить** `Modifier.padding(padding)`, контент окажется поверх bottom bar и под status bar. Это очень частый баг: «у меня LazyColumn перекрывается панелью внизу».

Правильный паттерн: **всегда применять `padding`** к корневому контейнеру content'а.

### `LazyVerticalGrid(GridCells.Adaptive(160.dp))` — как считается количество колонок

```kotlin
LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 160.dp),
    ...
)
```

`GridCells.Adaptive(160.dp)` — «динамически считай число колонок: положи столько, сколько влезет, при условии что каждая >= 160dp».

Алгоритм при ширине экрана `W`:
1. `n = floor(W / 160)`.
2. Реальная ширина каждой колонки: `W / n` (плюс/минус padding).

Численные примеры (без учёта padding):
- Телефон в портретной 360dp → `floor(360/160) = 2` колонки по ~180dp.
- Телефон в ландшафтной 720dp → `floor(720/160) = 4` колонки по ~180dp.
- Планшет 800dp → `floor(800/160) = 5` колонок по ~160dp.

Альтернатива — `GridCells.Fixed(n)`: ровно n колонок, всегда. Удобно, когда дизайн фиксированный (например, всегда 3 колонки).

Подвох: `LazyVerticalGrid` нельзя помещать в родителя без ограничения высоты — он внутри использует scroll, и если высота родителя бесконечная (например, внутри `Column` без `Modifier.weight(1f)`), Compose упадёт с `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`. См. подводный камень 7.

### `items(items = X, key = { it.id })` — зачем ключ

```kotlin
items(items = state.albums, key = { it.id }) { album -> ... }
```

`key` — это функция, которая для каждого элемента возвращает «стабильный идентификатор». Compose использует его, чтобы понять, какой item остался прежним при изменении списка.

Без ключа Compose сравнивает items по позиции в списке. Это значит:
- Если ты вставил элемент в начало — Compose думает, что **все** items изменились (на позиции 0 теперь новый элемент, на позиции 1 — то, что было на 0, и т.д.).
- ScrollState и анимации на этих items сбрасываются.

С ключом Compose понимает: «элемент с id=5 переехал с позиции 0 на позицию 1, но это **тот же** элемент». State сохраняется, анимации работают.

Правило: всегда `key` для items в `LazyColumn`/`LazyVerticalGrid`. Без него UI работает, но кривовато.

### `var title by remember { mutableStateOf("") }`

В диалоге создания альбома:

```kotlin
var title by remember { mutableStateOf("") }
```

Подробный разбор этой конструкции — в [`02-PERMISSIONS_AND_SCAN.md` → «Разбор по строкам» Шага 10](./02-PERMISSIONS_AND_SCAN.md). Кратко: три механизма в одной строке — `mutableStateOf` (наблюдаемый контейнер), `remember` (запоминание между рекомпозициями), `by` (property delegation, чтобы читать/писать без `.value`).

В диалоге это локальное UI-state, которое не нужно сохранять в ViewModel — он живёт только пока диалог открыт. После `onCreate(title.trim())` диалог закрывается, состояние умирает.

### `enableEdgeToEdge()` + `WindowInsets`

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { MainScaffold() } }
    }
}
```

`enableEdgeToEdge()` (из `androidx.activity`) делает status bar и navigation bar **прозрачными** и позволяет контенту рисоваться под ними. Современный Material 3 дизайн именно такой — нет «полосок» по краям, всё единое.

Подвох: контент теперь действительно лезет под bars, и если ты не учтёшь это, текст уедет под status bar. Решение — `WindowInsets`. `Scaffold` по умолчанию использует `contentWindowInsets = ScaffoldDefaults.contentWindowInsets`, который включает system bars. То есть `padding` в content-лямбде учтёт высоту status bar и navigation bar и поднимет контент.

Если в каком-то месте нужен ручной контроль:

```kotlin
Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
    Text("Заголовок не уедет под status bar")
}
```

### `BackHandler`

Системная кнопка «назад» вызывает `navController.popBackStack()`. Если надо своё поведение (например, закрыть `BottomSheet` вместо ухода с экрана) — используй:

```kotlin
BackHandler(enabled = sheetExpanded) {
    sheetExpanded = false
}
```

Если `enabled = false`, BackHandler не перехватывает — back уходит к навигации. Если `true` — перехватывает и выполняет лямбду вместо обычного back.

### Nested NavHost vs одна графа

Для простых приложений хватает одной `NavHost`. Для сложных делают nested — верхний граф для фичей (`navigation { composable<...>; composable<...> }`), внутри каждой — свой. Нам одной хватает.

### Локализация — как выбирается язык

Compose Resources читает `Locale.getDefault()`. На Android система локаль зависит от настроек устройства + настроек приложения (API 33+). Можешь протестировать, переключив язык системы в настройках телефона.

Чтобы пользователь мог переключать прямо в приложении — `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))` в MainActivity. В гайд не включаем, но знай про возможность.

### `AsyncImage` (Coil3)

```kotlin
AsyncImage(model = album.coverUri, contentDescription = album.title)
```

Загружает изображение по URI/URL/File. Поддерживает `content://` — для наших `albumart`-URI работает. Подробнее — в файле 08.

### Back stack при `navigate(Player)` с вкладки Tracks

Пользователь: Tracks → клик по треку → Player. Системный "назад" — возвращает на Tracks. Выходишь из приложения — если play всё ещё играет — сервис держит процесс. Это уже поведение из файла 04.

---

## Подводные камни

### 1. `@Serializable` без плагина
Забыл `alias(libs.plugins.kotlinSerialization)` в `build.gradle.kts` → при навигации `SerializationException`. Плагин обязателен.

### 2. Route объявлен как `class`, а не `data class`/`data object`
Без `data` — плохой equals/hashCode → навигация работает странно ("текущая destination" не совпадает). Всегда `data class`.

### 3. Разные типы Route в `composable<X>` и `navigate(Y)`
Если навигируешь `navigate(AlbumDetails("123"))`, а composable зарегистрирован только для `UserAlbumDetails` — `IllegalArgumentException` в runtime.

### 4. `NavController.currentBackStackEntryAsState()` → странные перезапуски
Он `State`, который меняется при навигации. Если в composable функции зависишь от него — перезапускается при каждом переходе. Если использовал в вычислении selected табa — отлично; если пытался `LaunchedEffect(currentBackStack)` — гарантированный бесконечный цикл.

### 5. Compose Resources strings не генерируются
Забыл блок `compose.resources { publicResClass = true }` → `Res.string.*` не виден. Либо pack not rebuilt — нужна полная пересборка (`./gradlew clean build`).

### 6. Две разные NavController в одном экране
Если в `MainScaffold` создал `rememberNavController()`, а внутри экрана — ещё один, то твой `navigate` пойдёт не туда, куда думаешь. Всегда передавай сверху вниз.

### 7. `LazyVerticalGrid` внутри `Column` без `heightIn`
`LazyVerticalGrid` нельзя помещать в родителя без ограничений высоты — вылетит `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`. Либо `Modifier.weight(1f)` в Column, либо родитель с фиксированной высотой.

### 8. Клик по `Card(onClick=...)` не реагирует
В Material3 `Card(onClick=...)` есть, но на Row/Column по умолчанию кликов нет. Используй `Modifier.clickable { ... }` или `ListItem`.

---

## Try yourself

1. **Убери edge-to-edge баги**: `enableEdgeToEdge()` включает прозрачные системные бары. Проверь, что `Scaffold` правильно поддвигает контент (`contentWindowInsets`).

2. **Переключи язык**: в настройках Android → системные языки → поставь English. Перезапусти приложение — весь UI должен стать английским.

3. **Добавь экран Search**: `@Serializable data object SearchRoute`, иконка в нижнем меню. Внутри — `OutlinedTextField` + результаты.

4. **Поменяй theme**: `MaterialTheme(colorScheme = darkColorScheme())` → приложение в тёмной теме. Потом сделай auto-detection через `isSystemInDarkTheme()`.

5. **`BackHandler` в Player**: на экране плеера кнопка "назад" должна закрыть экран, но не остановить воспроизведение. Проверь — так и работает? Если нет — добавь `BackHandler { onClose() }` в начало `PlayerScreen`.

6. **Deep link на альбом**: добавь в `composable<AlbumDetails>` параметр `deepLinks = listOf(navDeepLink { uriPattern = "app://album/{albumId}" })`. Проверь через `adb shell am start -a android.intent.action.VIEW -d "app://album/123"`.

---

## Дальше

→ [`08-COVER_ART.md`](./08-COVER_ART.md)

## Ссылки

- [Navigation Compose — Type-safe navigation](https://developer.android.com/guide/navigation/design/type-safety)
- [Navigation Compose KMP](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-navigation.html)
- [Compose Resources](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-resources.html)
- [Material 3 NavigationBar](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#NavigationBar%28androidx.compose.ui.Modifier,androidx.compose.ui.graphics.Color,androidx.compose.ui.graphics.Color,androidx.compose.ui.unit.Dp,androidx.compose.foundation.layout.WindowInsets,kotlin.Function1%29)
