# 06. ViewModel и управление состоянием

## Зачем

Экран — это функция от состояния. Задача ViewModel — **хранить и обновлять это состояние**, а также реагировать на пользовательские действия.

Три плохих подхода, которые мы избегаем:

1. **Состояние прямо в Composable через `remember { mutableStateOf(...) }`** — теряется при поворотах экрана, нельзя тестировать в отрыве от UI.
2. **Набор отдельных `StateFlow`** в ViewModel (`val tracks`, `val isLoading`, `val error`) — UI собирает по частям, легко забыть один, появляются несогласованные состояния (есть треки и одновременно `isLoading = true`).
3. **`mutableStateOf` в ViewModel** — работает, но теряется "строгая" реактивность: `StateFlow` можно перекладывать между слоями, комбинировать, а `State<T>` — нет.

Правильно — один `StateFlow<UiState>`, где `UiState` — data class со всеми полями экрана, и `SharedFlow<UiEvent>` для входных действий. Это называется **unidirectional data flow**: событие идёт в ViewModel, оттуда возвращается новый `UiState`.

---

## Что реализуем

1. Паттерн `UiState` / `UiEvent` на примере `TracksViewModel`.
2. Использование `stateIn` для превращения `Flow` из репозитория в `StateFlow`.
3. Обработка единоразовых событий (toast, навигация) через `SharedFlow<Effect>`.
4. Подпись на `StateFlow` в Compose через `collectAsStateWithLifecycle`.
5. Пример `PlayerViewModel` с подпиской на несколько источников.

Новые файлы (ViewModel + UiState для каждого экрана):

```
shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/
├── tracks/
│   ├── TracksViewModel.kt
│   ├── TracksUiState.kt
│   └── TracksEvent.kt
├── albums/
│   ├── AlbumsViewModel.kt
│   └── AlbumsUiState.kt
├── albumdetails/
│   └── AlbumDetailsViewModel.kt
├── player/
│   └── PlayerViewModel.kt
└── useralbums/
    └── UserAlbumsViewModel.kt
```

Здесь разберём паттерн подробно на `Tracks` и `Player`; остальные — по аналогии.

---

## Реализация

### Шаг 1 — `UiState`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/tracks/TracksUiState.kt
package org.example.mp3player.presentation.tracks

import org.example.mp3player.domain.Track

data class TracksUiState(
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
) {
    val filteredTracks: List<Track>
        get() = if (searchQuery.isBlank()) tracks
        else tracks.filter { t ->
            t.title.contains(searchQuery, ignoreCase = true) ||
                t.artist.contains(searchQuery, ignoreCase = true)
        }
}
```

Computed property `filteredTracks` — это простая оптимизация: нам не надо хранить отфильтрованный список отдельно, он зависит только от `tracks` и `searchQuery`. Пересчитывается на каждый рендер, но Compose умный — если результат тот же, не будет лишних перерисовок.

### Шаг 2 — `UiEvent`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/tracks/TracksEvent.kt
package org.example.mp3player.presentation.tracks

sealed interface TracksEvent {
    data object Load : TracksEvent
    data object Refresh : TracksEvent
    data class Search(val query: String) : TracksEvent
    data class PlayTrack(val index: Int) : TracksEvent
    data class AddToUserAlbum(val trackId: String, val albumId: Long) : TracksEvent
}
```

`sealed interface` — каждый экземпляр `TracksEvent` это один из перечисленных. В `when` компилятор проверит, что все ветки покрыты — забыл обработать новое событие → warning.

### Шаг 3 — `ViewModel`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/tracks/TracksViewModel.kt
package org.example.mp3player.presentation.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.example.mp3player.data.player.AudioPlayer
import org.example.mp3player.domain.TracksRepository
import org.example.mp3player.domain.UserAlbumsRepository

class TracksViewModel(
    private val tracksRepository: TracksRepository,
    private val userAlbumsRepository: UserAlbumsRepository,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _error = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)

    val state: StateFlow<TracksUiState> = combine(
        tracksRepository.observeTracks(),
        _searchQuery,
        _isLoading,
        _error,
    ) { tracks, query, loading, error ->
        TracksUiState(
            tracks = tracks,
            isLoading = loading,
            error = error,
            searchQuery = query,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TracksUiState(isLoading = true),
    )

    private val _effects = MutableSharedFlow<TracksEffect>()
    val effects: SharedFlow<TracksEffect> = _effects.asSharedFlow()

    fun onEvent(event: TracksEvent) {
        when (event) {
            TracksEvent.Load -> load()
            TracksEvent.Refresh -> refresh()
            is TracksEvent.Search -> _searchQuery.value = event.query
            is TracksEvent.PlayTrack -> playTrack(event.index)
            is TracksEvent.AddToUserAlbum -> addToAlbum(event.trackId, event.albumId)
        }
    }

    private fun load() {
        // Первая загрузка — делегируем репозиторию, observeTracks сам эмитнет.
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { tracksRepository.refresh() }
                .onFailure { _error.value = it.message ?: "Не удалось обновить" }
            _isLoading.value = false
        }
    }

    private fun playTrack(index: Int) {
        val current = state.value.filteredTracks
        if (index !in current.indices) return
        audioPlayer.play(current, startIndex = index)
        viewModelScope.launch { _effects.emit(TracksEffect.OpenPlayer) }
    }

    private fun addToAlbum(trackId: String, albumId: Long) {
        viewModelScope.launch {
            runCatching { userAlbumsRepository.addTrack(albumId, trackId) }
                .onSuccess { _effects.emit(TracksEffect.ShowMessage("Добавлено")) }
                .onFailure { _effects.emit(TracksEffect.ShowMessage("Ошибка: ${it.message}")) }
        }
    }
}

sealed interface TracksEffect {
    data object OpenPlayer : TracksEffect
    data class ShowMessage(val text: String) : TracksEffect
}
```

### Шаг 4 — Подписка в Compose

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/tracks/TracksScreen.kt
package org.example.mp3player.presentation.tracks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TracksScreen(
    onOpenPlayer: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: TracksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onEvent(TracksEvent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                TracksEffect.OpenPlayer -> onOpenPlayer()
                is TracksEffect.ShowMessage -> snackbar.showSnackbar(effect.text)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.onEvent(TracksEvent.Search(it)) },
            placeholder = { Text("Поиск") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
        )

        when {
            state.isLoading && state.tracks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.tracks.isEmpty() -> {
                ErrorBanner(
                    message = state.error!!,
                    onRetry = { viewModel.onEvent(TracksEvent.Refresh) },
                )
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.filteredTracks) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = { viewModel.onEvent(TracksEvent.PlayTrack(index)) },
                        )
                    }
                }
            }
        }
    }
}
```

### Шаг 5 — `PlayerViewModel` с несколькими источниками

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/player/PlayerViewModel.kt
package org.example.mp3player.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.example.mp3player.data.player.AudioPlayer
import org.example.mp3player.domain.PlaybackState

data class PlayerUiState(
    val title: String = "",
    val artist: String = "",
    val coverUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val progress: Float = 0f,
) {
    val positionText: String get() = formatDuration(positionMs)
    val durationText: String get() = formatDuration(durationMs)
}

sealed interface PlayerEvent {
    data object PlayPause : PlayerEvent
    data object Next : PlayerEvent
    data object Previous : PlayerEvent
    data class SeekTo(val positionMs: Long) : PlayerEvent
    data class SeekToFraction(val fraction: Float) : PlayerEvent
}

class PlayerViewModel(
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = audioPlayer.state
        .map { it.toUi() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerUiState(),
        )

    fun onEvent(event: PlayerEvent) {
        when (event) {
            PlayerEvent.PlayPause -> audioPlayer.toggle()
            PlayerEvent.Next -> audioPlayer.next()
            PlayerEvent.Previous -> audioPlayer.previous()
            is PlayerEvent.SeekTo -> audioPlayer.seekTo(event.positionMs)
            is PlayerEvent.SeekToFraction -> {
                val duration = state.value.durationMs
                if (duration > 0) audioPlayer.seekTo((duration * event.fraction).toLong())
            }
        }
    }

    private fun PlaybackState.toUi(): PlayerUiState {
        val track = currentTrack
        val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
        return PlayerUiState(
            title = track?.title.orEmpty(),
            artist = track?.artist.orEmpty(),
            coverUri = track?.coverUri,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            progress = progress.coerceIn(0f, 1f),
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
```

---

## Разбор

### `MutableStateFlow` vs `MutableSharedFlow` — фундаментальная разница

В этом ViewModel используются оба, и важно понимать, чем они отличаются концептуально.

| | `MutableStateFlow<T>` | `MutableSharedFlow<T>` |
|---|---|---|
| Хранит «текущее значение» | Да, всегда (`.value`) | Нет |
| Что получит новый подписчик | Текущее значение немедленно | Только то, что эмитится после подписки (если буфер 0) |
| Можно ли «потерять» эмит | Нет, но можно «conflate» (см. ниже) | Да, если буфер 0 и нет активных подписчиков |
| Аналогия | «Радио»: всегда что-то транслирует | «Чат»: пишешь сообщение, кто слушает — увидит |
| Для чего | UI-state (всегда нужен «текущий вид») | Эффекты (toast, navigation) — событие происходит один раз |

Почему так разделено: типичная ловушка новичка — положить «однократное событие» в `StateFlow`. Например, `val errorMessage: StateFlow<String?>`. Пользователь увидел toast, повернул экран — composable пересоздался, подписался на `StateFlow`, получил **то же самое значение** "Ошибка" — и toast показывается заново. Бесконечный цикл багов.

Со `SharedFlow` (без буфера) такого не происходит: подписался **после** эмита — не увидел его. Это и нужно для эффектов.

#### `conflated` поведение `StateFlow`

`StateFlow` всегда conflated: если за время, пока подписчик обрабатывает значение, ты эмитишь несколько новых — он увидит только последнее. Промежуточные **не** дойдут до подписчика. Для UI это нормально (нам важен последний снимок), но если нужно «не пропустить ни одного значения» — нужен `SharedFlow` с подходящим буфером.

#### `extraBufferCapacity` у `SharedFlow`

`MutableSharedFlow(extraBufferCapacity = 0)` — без буфера. Если эмитить, пока никто не слушает — emit заблокируется (suspend), пока не появится подписчик и не заберёт значение. Это безопасное поведение для эффектов: «ни одного toast не потеряем».

`extraBufferCapacity = 64` — буфер на 64 события. Если буфер заполнен и пришёл новый emit — поведение задаётся `onBufferOverflow`: `SUSPEND` (ждать), `DROP_OLDEST`, `DROP_LATEST`.

В нашем `_effects = MutableSharedFlow<TracksEffect>()` буфера нет — этого хватает: эффекты редкие, подписчик активный.

### `combine(flowA, flowB, ...) { a, b, ... -> ... }` — timing

```kotlin
combine(
    tracksRepository.observeTracks(),
    _searchQuery,
    _isLoading,
    _error,
) { tracks, query, loading, error -> TracksUiState(...) }
```

Что физически делает `combine`:

1. Подписывается на все входные `Flow` параллельно.
2. **Ждёт первого эмита от каждого** из них. Пока хотя бы один молчит — `combine` не эмитит.
3. Как только все выдали хотя бы одно значение — лямбда вызывается с этими значениями, `combine` эмитит результат.
4. Дальше: каждый раз, когда **любой** из входов эмитит новое значение, `combine` пересчитывает результат с новейшими значениями всех входов.

В нашем случае все четыре источника — это либо `MutableStateFlow` с `initialValue` (тут же эмитят начальное), либо `Flow` от репозитория, который через `.asStateFlow()` тоже сразу эмитит. Поэтому первый эмит `combine` практически мгновенный.

Подвох, если один из источников — холодный `Flow`, который ничего не эмитит без триггера: `combine` будет тихо молчать. Не будет ошибки, не будет initial value — ничего. Симптом: `state` зависает на `initialValue` навсегда. Решение — либо использовать только `StateFlow`-источники, либо `flow.onStart { emit(...) }` для гарантированного первого значения.

### `viewModelScope` — откуда и когда умирает

```kotlin
viewModelScope.launch { ... }
```

`viewModelScope` — это extension-property у `androidx.lifecycle.ViewModel`, объявленная в `lifecycle-viewmodel-ktx`:

```kotlin
public val ViewModel.viewModelScope: CoroutineScope
    get() = this.getTag(JOB_KEY) ?: setTagIfAbsent(JOB_KEY, CloseableCoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
```

Что важно:
- Scope привязан к `ViewModel`. У каждой ViewModel свой.
- Под капотом — `SupervisorJob` + `Dispatchers.Main.immediate`. `Supervisor` означает, что падение одной дочерней корутины не убивает остальные. `Main.immediate` — оптимизация: если уже на main, не диспатчиться через очередь.
- В `ViewModel.onCleared()` (вызывается, когда ViewModel больше не нужна) scope **отменяется**. Все запущенные `viewModelScope.launch { ... }` падают с `CancellationException`, ресурсы освобождаются.

Это значит: если в `viewModelScope.launch { while(true) { ... } }` ты накодил бесконечный цикл — он **не утечёт**. При выходе с экрана ViewModel умрёт, scope отменится, цикл прервётся.

Но: если внутри цикла ты не уважаешь cancellation (не делаешь `delay()` или другую suspend-функцию) — корутина не сможет отмениться. `while(true) { sum += 1 }` в `viewModelScope` — утечка, потому что cancellation проверяется только в suspend-точках. Всегда `delay`/`yield`/любой `suspend`-call внутри тяжёлых циклов.

### `stateIn` — глубокий разбор

```kotlin
.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = TracksUiState(isLoading = true),
)
```

Превращает любой `Flow<T>` в `StateFlow<T>`. Чтобы понять, что физически происходит, нужно сначала понять разницу холодного и горячего потоков.

#### Холодный vs горячий поток

- **Холодный (`Flow`).** Аналогия: видеокассета. Пока никто не вставил кассету и не нажал play — ничего не происходит. Каждый, кто вставит свою кассету, увидит запись с начала.
- **Горячий (`StateFlow`, `SharedFlow`).** Аналогия: радиоэфир. Эфир идёт всегда, независимо от слушателей. Кто включил приёмник — слышит то, что играет прямо сейчас.

`tracksRepository.observeTracks()` после `stateIn` уже не cold-flow. Оно «горячее» внутри `viewModelScope`.

#### Что физически делает `stateIn`

1. Создаёт **одну upstream-корутину** в указанном `scope` — это и есть «приёмник» исходного `Flow`.
2. Запускает (или нет — зависит от `started`) подписку на upstream.
3. Каждый эмит upstream'а кладёт в внутренний `MutableStateFlow.value`.
4. Возвращает наружу `StateFlow<T>` — read-only обёртку над этим внутренним state'ом.

Все downstream-подписчики (твоя UI) подписываются не на исходный `Flow` напрямую, а на этот общий `StateFlow`. Это — **ключевая** оптимизация: один upstream обслуживает многих подписчиков.

#### `SharingStarted` варианты

- **`Eagerly`** — upstream-корутина стартует сразу при `stateIn`, живёт всё время, пока живёт `scope`. Удобно для критичных данных (плеер, настройки), которые должны быть готовы мгновенно.
- **`Lazily`** — upstream стартует при появлении первого подписчика, дальше живёт всё время `scope`. Промежуточный вариант: «не запускай зря, но потом не отписывайся».
- **`WhileSubscribed(stopTimeoutMillis)`** — upstream стартует при первом подписчике; когда последний подписчик отписался, upstream продолжает работать ещё `stopTimeoutMillis` мс, и если за это время никто не подписался обратно — отменяется.

Зачем 5_000 мс в `WhileSubscribed`: при повороте экрана Composable destroy → recreate занимает считанные мс. Если бы стояло `WhileSubscribed(0)`, между двумя версиями экрана upstream бы успел умереть, и новый экран запустил бы upstream заново — лишняя работа (повторное сканирование, новый запрос к Room и т.п.). 5 секунд — типичный «зазор», достаточный для recreate, недостаточный для «юзер вернулся с другого экрана через минуту».

#### Что произойдёт, если 6 секунд без подписчиков, потом новый

Сценарий с `WhileSubscribed(5_000)`:
1. `t=0`: подписчик ушёл. Upstream живёт.
2. `t=5000`: за 5 секунд никто не подписался. Upstream cancel'ится.
3. `t=6000`: появился новый подписчик. Upstream стартует **заново**. Подписчик сначала видит `initialValue` (или последнее значение, если оно было — зависит от того, не сбросило ли его cancel).

Поэтому `initialValue` — не «значение в самом начале и забудь», а «значение, которое видит подписчик, когда апстрим ещё не выдал ничего» — это может случиться многократно.

### `collectAsStateWithLifecycle` vs `collectAsState`

`collectAsState` (из `androidx.compose.runtime`):
- Подписывается, пока Composable в composition.
- Когда экран ушёл в фон (Activity STOPPED), подписка **остаётся активной**.
- Это значит: даже если экран не виден, апстрим работает, обновления приходят впустую — батарея садится.

`collectAsStateWithLifecycle` (из `androidx.lifecycle:lifecycle-runtime-compose`):
- Регистрирует `LifecycleEventObserver`.
- При `ON_START` → подписка на flow.
- При `ON_STOP` → подписка отменяется.
- Возвращает обратно к `ON_START`.

Эффект: пока экран в фоне, ничего не collect'ится. Это работает в паре со `WhileSubscribed(5_000)` — оба ждут «свой» таймаут, и если экран в фоне дольше 5 секунд, апстрим тоже отменяется, до возврата.

**Вывод:** для UI всегда `collectAsStateWithLifecycle`. `collectAsState` оставлен в API из соображений совместимости и для случаев, когда lifecycle неактуален.

### `LaunchedEffect(key)` — что физически

```kotlin
LaunchedEffect(Unit) {
    viewModel.onEvent(TracksEvent.Load)
}
```

`LaunchedEffect(key1) { блок }` — composable-функция, которая:
1. При первом появлении в composition запускает корутину в специальном scope, выполняя `блок`.
2. Если `key1` изменился между рекомпозициями — текущая корутина **отменяется**, и запускается новая.
3. Если Composable уходит из composition — корутина отменяется.

**`Unit` как ключ** — стабильное значение, которое никогда не меняется → корутина запускается ровно **один раз**, при появлении Composable.

Типичная ловушка: написать `LaunchedEffect(state)`, где `state` — это твой `UiState`. Каждое изменение state создаёт новый объект (data class) → ключ меняется → корутина перезапускается на каждый эмит. Симптом: «мой эффект почему-то выполняется несколько раз».

Правило: **ключ должен меняться ровно тогда, когда ты хочешь перезапустить эффект**. Не чаще.

`LaunchedEffect(key1, key2, ...)` — несколько ключей, корутина перезапускается при изменении любого.

### `collectLatest` vs `collect`

```kotlin
viewModel.effects.collectLatest { effect -> ... }
```

`collect { блок }`:
- Получает значение → выполняет блок → ждёт следующего значения.
- Если в `блок` стоит `delay(1000)` и за это время пришли 5 эмитов — все 5 будут обработаны последовательно (с паузами).

`collectLatest { блок }`:
- Получает значение → запускает блок в новой корутине.
- Если пришёл новый эмит, **пока блок ещё работает** — текущий блок отменяется, новый эмит запускается с нуля.

В нашем случае `collectLatest` для эффектов — спорный выбор: если за время показа toast пришёл новый — мы **отменим** показ старого. Чаще для эффектов хочется видеть **все** эмиты по порядку → `collect`.

Где `collectLatest` действительно нужен:
- Дебаунс: на каждый ввод текста перезапускать поиск, отменяя предыдущий.
- Загрузка изображения: пользователь свайпнул → отменить загрузку прошлой картинки.

```kotlin
searchQuery.collectLatest { query ->
    delay(300)            // пользователь продолжает печатать → отмена
    val results = api.search(query)
    _results.value = results
}
```

### `runCatching { ... }` — ловушка с `CancellationException`

```kotlin
runCatching { tracksRepository.refresh() }
    .onSuccess { ... }
    .onFailure { ... }
```

`runCatching` — Kotlin-сахар вокруг `try/catch`. Возвращает `Result<T>`, с которым можно цепочкой через `onSuccess`/`onFailure`/`map`/`recover`.

**Проблема в корутинах:** `runCatching` ловит **все** `Throwable`, включая `CancellationException`. А `CancellationException` — это особый сигнал: «отмени корутину». Он **должен** пробрасываться вверх, иначе корутина не остановится корректно.

Сценарий бага:
1. Пользователь нажал «Refresh» → `viewModelScope.launch { ... runCatching { tracksRepository.refresh() } ... }`.
2. Через 100 мс пользователь ушёл с экрана → `viewModelScope` отменяется.
3. Cancellation идёт вниз: `refresh()` бросает `CancellationException`.
4. `runCatching` **ловит** его и кладёт в `Result.failure(CancellationException(...))`.
5. `onFailure` срабатывает → `_error.value = "JobCancellation: ..."`.
6. Пользователь возвращается на экран → видит баннер с непонятной ошибкой.

Правильный паттерн:

```kotlin
runCatching { tracksRepository.refresh() }
    .onFailure { e ->
        if (e is CancellationException) throw e   // пробрасываем дальше
        _error.value = e.message ?: "Не удалось обновить"
    }
    .onSuccess { ... }
```

Или явный `try/catch (e: Exception)` (он не ловит `CancellationException`, потому что в kotlinx-coroutines `CancellationException` — это `IllegalStateException`-подобное, и формально оно `Exception`, поэтому проверять надо специально):

```kotlin
try {
    tracksRepository.refresh()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    _error.value = e.message
}
```

В коде гайда выше я оставил «короткую» форму ради компактности — но в реальном проекте это первое, что нужно поправить.

### Почему computed property, а не держать поле?

```kotlin
data class TracksUiState(
    val tracks: List<Track>,
    val searchQuery: String,
) {
    val filteredTracks: List<Track> get() = if (searchQuery.isBlank()) tracks
        else tracks.filter { ... }
}
```

`val ... get() = ...` — это **computed property**: не поле, а функция, замаскированная под свойство. На каждый доступ к `state.filteredTracks` лямбда `get` выполняется заново.

Альтернатива:

```kotlin
data class TracksUiState(
    val allTracks: List<Track>,
    val filteredTracks: List<Track>,  // должен быть синхронен с allTracks + searchQuery
    val searchQuery: String,
)
```

Со вторым вариантом надо **руками** следить, чтобы `filteredTracks` был действительно `filter(allTracks, searchQuery)`. Где-то в `combine { ... }` забыл пересчитать → state несогласован.

С computed `get()` это **невозможно** — функция всегда выдаёт правильный результат, выводя его из других полей.

Цена: на каждый рендер `LazyColumn` Compose читает `state.filteredTracks` — фильтрация выполняется заново. На 5000 треков — несколько мс. Если профилировщик показал, что это медленно, можно мемоизировать через `remember(state.tracks, state.searchQuery) { state.tracks.filter { ... } }` прямо в Composable. Но обычно эта оптимизация не нужна.

### `Flow<X>.map { it.toUi() }` — это `Flow.map`, не `List.map`

```kotlin
val state: StateFlow<PlayerUiState> = audioPlayer.state
    .map { it.toUi() }
    .stateIn(...)
```

Здесь `.map` — это `Flow.map` (импорт `kotlinx.coroutines.flow.map`). Он принимает лямбду `(T) -> R` и возвращает новый `Flow<R>`, который применяет лямбду к каждому upstream-эмиту.

Не путай с `List<T>.map { ... }: List<R>` — это синхронная трансформация коллекции (см. подробное сравнение в [`02-PERMISSIONS_AND_SCAN.md` → «Разбор по строкам» Шага 8](./02-PERMISSIONS_AND_SCAN.md#разбор-по-строкам)).

Лайфхак: если в импортах файла нет `kotlinx.coroutines.flow.map`, но `.map` всё равно компилируется — IDE мне предлагает auto-import. Проверь, что взялся именно `Flow.map`, а не `List.map` (если в receiver случайно List).

---

## Подводные камни

### 1. `stateIn` с `SharingStarted.Eagerly`
Используй только для критичных данных (плеер, настройки). Для обычных экранов `WhileSubscribed(5000)` экономит батарею и CPU.

### 2. `collect` в `LaunchedEffect(Unit)` дважды
```kotlin
LaunchedEffect(Unit) { viewModel.effects.collect { ... } }
LaunchedEffect(Unit) { viewModel.someOtherFlow.collect { ... } }
```
Два `LaunchedEffect(Unit)` — это нормально (разные id). Но в одном экземпляре `viewModel.effects` использовать **`collectLatest`** не везде — потеряешь события, если они быстрее, чем лямбда обрабатывает. Для UI `collectLatest` обычно ок.

### 3. `mutableStateOf` в ViewModel
Работает, но не работает с `combine`, с flow-операторами, с тестами на Flow. Держись `StateFlow`.

### 4. Блокирующий код в `onEvent`
`onEvent` не `suspend`. Если нужна асинхронность — `viewModelScope.launch { }`. Никогда не `Thread.sleep` или `runBlocking`.

### 5. Гигантские `UiState`
Если data class разрастается до 20 полей — подумай, не пора ли разбить экран на под-компоненты со своим state. Для одного экрана 5-10 полей — нормально.

### 6. События через `StateFlow`
```kotlin
val snackbarMessage: StateFlow<String?>  // ❌
```
При повороте — снова "saved" всплывает. Используй `SharedFlow` или паттерн "одноразовое событие".

### 7. `viewModelScope.launch { while(true) }` без остановки
Утечка корутины при `viewModelScope.cancel` → ОК. Но если `while(true)` без `delay`, съешь CPU. Всегда `delay` или другой suspending-call внутри.

### 8. `repository.observeFoo()` напрямую в Composable
```kotlin
val tracks by tracksRepository.observeTracks().collectAsState(emptyList())  // ❌
```
UI напрямую зависит от data. Тест UI теперь требует `TracksRepository`. Всегда через ViewModel.

---

## Try yourself

1. **Добавь сортировку**: `TracksEvent.SetSortOrder(SortOrder)`, поле `sortOrder` в `UiState`, pre-filter-сортировка в computed `filteredTracks`.

2. **"Нет треков" vs "Нет результатов поиска"**: два разных пустых состояния. В `TracksScreen` при `state.tracks.isEmpty()` показать "Нет музыки", при `state.filteredTracks.isEmpty() && state.tracks.isNotEmpty()` — "Ничего не найдено по запросу".

3. **Сохранение query при повороте**: проверь — работает ли поиск после поворота? (Должен, т.к. state в ViewModel.) Теперь посмотри: fokus поля ввода теряется? Это задача Compose, через `FocusRequester`.

4. **Unit-тест `TracksViewModel`**: fake `TracksRepository` возвращает `flowOf(listOf(track1, track2))`. После `onEvent(Search("query"))` → `state.filteredTracks` соответствующий.

5. **`PlayerViewModel` — shuffle**: добавь `PlayerEvent.ToggleShuffle`, поле `shuffleEnabled` в state, используй `audioPlayer.setShuffleEnabled`.

---

## Дальше

→ [`07-NAVIGATION_AND_SCREENS.md`](./07-NAVIGATION_AND_SCREENS.md)

## Ссылки

- [StateFlow and SharedFlow — Kotlin docs](https://kotlinlang.org/docs/flow.html#stateflow-and-sharedflow)
- [Architecture: UI layer](https://developer.android.com/topic/architecture/ui-layer)
- [`stateIn` reference](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/state-in.html)
- [Side-effects in Compose](https://developer.android.com/jetpack/compose/side-effects)
