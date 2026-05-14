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

`UiState` — один data class со всеми полями экрана. Снимок «что должно быть на экране прямо сейчас». ViewModel будет выдавать `StateFlow<UiState>`, экран подписывается и рисует.

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

Все поля с дефолтами — это «пустое» state при первой загрузке экрана. data class даёт `equals`/`hashCode` автоматически — это важно для `StateFlow.distinctUntilChanged`, чтобы не было лишних эмитов при одинаковых значениях.

**`filteredTracks` — computed property.** `val ... get() = ...` это **не поле**, а функция, замаскированная под свойство. На каждый доступ к `state.filteredTracks` лямбда `get` выполняется заново.

Альтернатива — хранить `filteredTracks` отдельным полем и держать его синхронным с `tracks` + `searchQuery`. Минус: где-то в `combine { ... }` забыл пересчитать → state несогласован. С computed `get()` это невозможно — функция всегда выдаёт правильный результат, выводя его из других полей.

Цена: на каждый рендер `LazyColumn` Compose читает `state.filteredTracks` — фильтрация выполняется заново. На 5000 треков — несколько мс. Если профилировщик покажет, что медленно, можно мемоизировать через `remember(state.tracks, state.searchQuery) { ... }` прямо в Composable. Обычно эта оптимизация не нужна.

### Шаг 2 — `UiEvent`

`UiEvent` — что пользователь может сделать на экране. Sealed interface с конкретными подтипами под каждый тип события.

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

`sealed interface` — каждый экземпляр `TracksEvent` это один из перечисленных. В `when` компилятор проверит, что все ветки покрыты — забыл обработать новое событие → warning или ошибка (если `when` используется как выражение).

`data object Load` — события без полей (используются для команд «выполни X»). `data class Search(val query: String)` — события с данными.

### Шаг 3 — `ViewModel`

ViewModel — самая сложная часть паттерна. У неё несколько ответственностей: подписаться на источники данных, скомбинировать их в `UiState`, отдавать `StateFlow` наружу, плюс обрабатывать события и слать одноразовые эффекты (toast, navigation).

Создаём файл — пакет, импорты, объявление класса с DI через конструктор:

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

    // дальше — приватные источники state (_searchQuery, _error, _isLoading), публичный state через combine+stateIn, _effects SharedFlow, onEvent диспатчер, приватные обработчики, и sealed interface TracksEffect
}
```

Зависимости приходят через конструктор — Koin из этапа 5 сам соберёт. ViewModel не знает про их реализацию, только про интерфейсы (`TracksRepository`, `UserAlbumsRepository`) и `AudioPlayer`.

`ViewModel` из `androidx.lifecycle` — наследуемся, чтобы получить `viewModelScope`. Под капотом это `SupervisorJob + Dispatchers.Main.immediate`, который **отменяется** в `onCleared()` (когда ViewModel больше не нужна). Все корутины, запущенные в `viewModelScope.launch { ... }`, автоматически прерываются — никаких утечек.

Внутрь добавляем приватные источники state — то, чем сама ViewModel «крутит» (в отличие от `observeTracks()`, который приходит из репозитория):

```kotlin
class TracksViewModel(...) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _error = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)

    // дальше — публичный state (combine+stateIn), _effects SharedFlow, onEvent, обработчики
}
```

`MutableStateFlow` — контейнер с **ровно одним текущим значением** (см. подробнее в 02 Шаг 7). Подходит для UI-state: мы хотим знать «текущее значение поиска», а не «всю историю изменений».

Дальше — главное: публичный `state: StateFlow<TracksUiState>` через `combine` + `stateIn`:

```kotlin
class TracksViewModel(...) : ViewModel() {

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

    // дальше — _effects SharedFlow, onEvent диспатчер, приватные обработчики, sealed interface TracksEffect
}
```

**`combine(flowA, flowB, ...) { ... } — timing.** Что физически делает `combine`:

1. Подписывается на все входные `Flow` параллельно.
2. **Ждёт первого эмита от каждого** из них. Пока хотя бы один молчит — `combine` не эмитит.
3. Как только все выдали хотя бы одно значение — лямбда вызывается с этими значениями, `combine` эмитит результат.
4. Дальше: каждый раз, когда **любой** из входов эмитит новое значение, `combine` пересчитывает результат с новейшими значениями всех входов.

В нашем случае все четыре источника — это либо `MutableStateFlow` с initial-value, либо `Flow` от репозитория, который через `.asStateFlow()` тоже сразу эмитит. Поэтому первый эмит `combine` практически мгновенный.

Подвох: если один из источников — холодный `Flow`, который ничего не эмитит без триггера, `combine` будет тихо молчать. Не будет ошибки, не будет initial value — ничего. Симптом: `state` зависает на `initialValue` навсегда. Решение — либо использовать только `StateFlow`-источники, либо `flow.onStart { emit(...) }`.

**`stateIn` — превращает `Flow` в `StateFlow`.** Чтобы понять, что делает, надо различать холодный и горячий потоки:

- **Холодный (`Flow`).** Аналогия: видеокассета. Пока никто не вставил кассету и не нажал play — ничего не происходит. Каждый, кто вставит свою — увидит запись с начала.
- **Горячий (`StateFlow`, `SharedFlow`).** Аналогия: радиоэфир. Эфир идёт всегда, независимо от слушателей. Кто включил приёмник — слышит то, что играет прямо сейчас.

После `stateIn` наш `combine` уже не cold-flow. Что физически делает `stateIn`:

1. Создаёт **одну upstream-корутину** в указанном `scope` — это «приёмник» исходного `Flow`.
2. Запускает (или нет — зависит от `started`) подписку на upstream.
3. Каждый эмит upstream'а кладёт во внутренний `MutableStateFlow.value`.
4. Возвращает наружу `StateFlow<T>` — read-only обёртку над этим внутренним state'ом.

Все downstream-подписчики (твоя UI) подписываются не на исходный `Flow` напрямую, а на этот общий `StateFlow`. Это — **ключевая** оптимизация: один upstream обслуживает многих подписчиков.

**`SharingStarted` варианты:**

- **`Eagerly`** — upstream-корутина стартует сразу при `stateIn`, живёт всё время, пока живёт `scope`. Для критичных данных (плеер, настройки).
- **`Lazily`** — upstream стартует при первом подписчике, дальше живёт всё время `scope`.
- **`WhileSubscribed(stopTimeoutMillis)`** — upstream стартует при первом подписчике; когда последний отписался, upstream живёт ещё `stopTimeoutMillis` мс, потом отменяется.

Зачем `5_000` мс в `WhileSubscribed`: при повороте экрана Composable destroy → recreate занимает считанные мс. С `WhileSubscribed(0)` между двумя версиями экрана upstream бы успел умереть, и новый экран запустил бы его заново — лишняя работа. 5 секунд — типичный «зазор», достаточный для recreate, недостаточный для «юзер вернулся через минуту».

`initialValue = TracksUiState(isLoading = true)` — значение, которое видит подписчик, **пока upstream ещё не выдал ничего**. Это может случиться многократно (например, после `WhileSubscribed` cancel и нового подписчика через 6 секунд — upstream стартует заново, до первого эмита подписчик видит initial).

Теперь — `SharedFlow` для одноразовых эффектов (toast, навигация):

```kotlin
class TracksViewModel(...) : ViewModel() {

    // state выше

    private val _effects = MutableSharedFlow<TracksEffect>()
    val effects: SharedFlow<TracksEffect> = _effects.asSharedFlow()

    // дальше — onEvent диспатчер и приватные обработчики
}
```

**`MutableStateFlow` vs `MutableSharedFlow` — фундаментальная разница.** В одной ViewModel используются оба:

| | `MutableStateFlow<T>` | `MutableSharedFlow<T>` |
|---|---|---|
| Хранит «текущее значение» | Да, всегда (`.value`) | Нет |
| Что получит новый подписчик | Текущее значение немедленно | Только то, что эмитится после подписки (если буфер 0) |
| Можно ли «потерять» эмит | Нет, но можно «conflate» (см. ниже) | Да, если буфер 0 и нет активных подписчиков |
| Аналогия | «Радио»: всегда что-то транслирует | «Чат»: пишешь сообщение, кто слушает — увидит |
| Для чего | UI-state (всегда нужен «текущий вид») | Эффекты (toast, navigation) — событие происходит один раз |

Почему так разделено: типичная ловушка — положить «однократное событие» в `StateFlow`. Например, `val errorMessage: StateFlow<String?>`. Пользователь увидел toast, повернул экран — composable пересоздался, подписался, получил **то же самое значение** "Ошибка" — toast показывается заново. Бесконечный цикл.

Со `SharedFlow` без буфера такого не происходит: подписался **после** эмита — не увидел его. Это и нужно для эффектов.

`StateFlow` всегда **conflated**: если за время, пока подписчик обрабатывает значение, ты эмитишь несколько новых — он увидит только последнее. Промежуточные **не** дойдут. Для UI это нормально (нам важен последний снимок).

`MutableSharedFlow<TracksEffect>()` без аргументов — буфера нет. Если эмитить, пока никто не слушает, `emit` заблокируется (suspend) до подписчика. Безопасное поведение для эффектов: «ни одного toast не потеряем».

`extraBufferCapacity = 64` — буфер на 64 события. Если буфер полон, поведение задаётся `onBufferOverflow`: `SUSPEND` / `DROP_OLDEST` / `DROP_LATEST`.

`asSharedFlow()` — апкаст до read-only типа (как `asStateFlow()`).

Диспатчер событий — единственная точка входа для UI:

```kotlin
class TracksViewModel(...) : ViewModel() {

    // state, _effects выше

    fun onEvent(event: TracksEvent) {
        when (event) {
            TracksEvent.Load -> load()
            TracksEvent.Refresh -> refresh()
            is TracksEvent.Search -> _searchQuery.value = event.query
            is TracksEvent.PlayTrack -> playTrack(event.index)
            is TracksEvent.AddToUserAlbum -> addToAlbum(event.trackId, event.albumId)
        }
    }

    // дальше — приватные обработчики load/refresh/playTrack/addToAlbum
}
```

`when` на `sealed interface` — компилятор проверит exhaustiveness. Добавил новое событие → ошибка компиляции, пока не добавишь ветку. Гарантирует, что все события обрабатываются.

Простой случай `Search` — синхронно обновляем `_searchQuery.value`. `combine` сразу пересчитает `state`.

И финал — приватные обработчики тяжёлых событий:

```kotlin
class TracksViewModel(...) : ViewModel() {

    // ... всё выше ...

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

`viewModelScope.launch` — корутина, которая отменится при `onCleared()`. Если экран закрылся в момент `refresh()` — корутина прервётся, ничего не утечёт. Если внутри цикла без suspend-точек (`while(true) { sum += 1 }`) — корутина не сможет отмениться, cancellation проверяется только в suspend-точках. Всегда `delay`/`yield`/любой `suspend`-call внутри тяжёлых циклов.

**`runCatching { ... }` — ловушка с `CancellationException`.** Это Kotlin-сахар вокруг `try/catch`. Возвращает `Result<T>`, с которым удобно работать через `onSuccess`/`onFailure`.

Проблема в корутинах: `runCatching` ловит **все** `Throwable`, включая `CancellationException`. А `CancellationException` — особый сигнал: «отмени корутину». Он **должен** пробрасываться вверх, иначе корутина не остановится корректно.

Сценарий бага:
1. Пользователь нажал «Refresh» → `viewModelScope.launch { runCatching { tracksRepository.refresh() } }`.
2. Через 100 мс ушёл с экрана → `viewModelScope` отменяется.
3. Cancellation идёт вниз: `refresh()` бросает `CancellationException`.
4. `runCatching` ловит его и кладёт в `Result.failure(CancellationException(...))`.
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

Или явный `try/catch (e: CancellationException) { throw e } catch (e: Exception) { ... }`. В коде выше я оставил «короткую» форму ради компактности — в реальном проекте это первое, что нужно поправить.

`playTrack` — читает `state.value` (тот самый snapshot из `StateFlow`), берёт `filteredTracks`, передаёт в `audioPlayer.play(...)`. Затем эмитит `OpenPlayer` в effects — экран увидит и навигирует.

`addToAlbum` — типичный паттерн «вызов с обратной связью через toast». `onSuccess`/`onFailure` эмитят сообщение пользователю.

### Шаг 4 — Подписка в Compose

Экран — это `@Composable`-функция, которая подписывается на `state: StateFlow<TracksUiState>` и `effects: SharedFlow<TracksEffect>` от ViewModel, и шлёт события через `onEvent(...)`.

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

Разбираем по строкам.

**`val state by viewModel.state.collectAsStateWithLifecycle()`** — подписка на `StateFlow`.

`collectAsState` (из `androidx.compose.runtime`):
- Подписывается, пока Composable в composition.
- Когда экран ушёл в фон (Activity STOPPED), подписка **остаётся активной**.
- Это значит: даже если экран не виден, апстрим работает, обновления приходят впустую — батарея садится.

`collectAsStateWithLifecycle` (из `androidx.lifecycle:lifecycle-runtime-compose`):
- Регистрирует `LifecycleEventObserver`.
- При `ON_START` → подписка на flow.
- При `ON_STOP` → подписка отменяется.

Эффект: пока экран в фоне, ничего не collect'ится. Это работает в паре со `WhileSubscribed(5_000)` — оба ждут «свой» таймаут, и если экран в фоне дольше 5 секунд, апстрим тоже отменяется, до возврата.

**Вывод:** для UI всегда `collectAsStateWithLifecycle`. `collectAsState` оставлен для случаев, когда lifecycle неактуален.

`by` — property delegation: `state` ведёт себя как `TracksUiState`, под капотом каждое чтение идёт в `MutableState.value` (см. подробнее в 02 Шаг 10).

**`LaunchedEffect(Unit) { ... }` — что физически.**

`LaunchedEffect(key1) { блок }` — composable-функция, которая:
1. При первом появлении в composition запускает корутину в специальном scope, выполняя `блок`.
2. Если `key1` изменился между рекомпозициями — текущая корутина **отменяется**, и запускается новая.
3. Если Composable уходит из composition — корутина отменяется.

**`Unit` как ключ** — стабильное значение, которое никогда не меняется → корутина запускается ровно **один раз**, при появлении Composable.

Типичная ловушка: написать `LaunchedEffect(state)`, где `state` — это твой `UiState`. Каждое изменение state создаёт новый объект (data class) → ключ меняется → корутина перезапускается на каждый эмит. Симптом: «мой эффект почему-то выполняется несколько раз».

Правило: **ключ должен меняться ровно тогда, когда ты хочешь перезапустить эффект**.

В нашем случае два `LaunchedEffect(Unit)`:
- Первый — стартует первоначальную загрузку при появлении экрана.
- Второй — подписывается на `effects` через `collectLatest` и реагирует на каждый эффект.

**`collectLatest` vs `collect`.**

`collect { блок }`:
- Получает значение → выполняет блок → ждёт следующего значения.
- Если в `блок` стоит `delay(1000)` и за это время пришли 5 эмитов — все 5 будут обработаны последовательно (с паузами).

`collectLatest { блок }`:
- Получает значение → запускает блок в новой корутине.
- Если пришёл новый эмит, **пока блок ещё работает** — текущий блок отменяется, новый эмит запускается с нуля.

В нашем случае `collectLatest` для эффектов — спорный выбор: если за время показа toast пришёл новый — мы **отменим** показ старого. Чаще для эффектов хочется видеть **все** эмиты по порядку → `collect`.

Где `collectLatest` действительно нужен:
- Дебаунс: на каждый ввод текста перезапускать поиск, отменяя предыдущий.
- Загрузка изображения: пользователь свайпнул → отменить загрузку прошлой.

```kotlin
searchQuery.collectLatest { query ->
    delay(300)            // пользователь продолжает печатать → отмена
    val results = api.search(query)
    _results.value = results
}
```

Тело `TracksScreen` — стандартный pattern «один большой `when`, который выбирает что показать по состоянию». `state.isLoading && state.tracks.isEmpty()` — спиннер показываем только при **первой** загрузке (когда треков ещё нет). На последующих refresh'ах треки уже есть — пользователь увидит баннер или старый список.

`itemsIndexed` — вариант `items` с индексом, нужен потому что `TracksEvent.PlayTrack` принимает `index`. UI-слой просто передаёт его, не зная про логику.

### Шаг 5 — `PlayerViewModel` с несколькими источниками

`PlayerViewModel` проще — у нас уже есть готовый `audioPlayer.state: StateFlow<PlaybackState>` (из этапа 4). ViewModel просто мапит его в `PlayerUiState` для экрана. Никакого `combine` — один источник.

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

В цепочке `audioPlayer.state.map { it.toUi() }.stateIn(...)` `.map` — это **`Flow.map`** (импорт `kotlinx.coroutines.flow.map`), не `List.map`. Принимает лямбду `(T) -> R` и возвращает новый `Flow<R>`, который применяет лямбду к каждому upstream-эмиту. Сравнение с `List.map` подробно — в `02-PERMISSIONS_AND_SCAN.md`, Шаг 8.

Лайфхак: если в импортах файла нет `kotlinx.coroutines.flow.map`, но `.map` всё равно компилируется — IDE предлагает auto-import. Проверь, что взялся именно `Flow.map`, а не `List.map` (если в receiver случайно `List`).

`PlayerUiState` имеет computed `positionText`/`durationText` — тот же паттерн, что в `TracksUiState.filteredTracks` (см. Шаг 1).

`SeekToFraction` — пример события «преобразовать UI-input во внутреннюю единицу». В Slider Material 3 значение — `0f..1f`; ViewModel вычисляет миллисекунды через `(duration * fraction).toLong()`.

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
