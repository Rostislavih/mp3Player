# 06. ViewModel и управление состоянием

## Зачем

Экран — это функция от состояния. Задача ViewModel — **хранить и обновлять это состояние**, а также реагировать на пользовательские действия.

Три плохих подхода, которые мы избегаем:

1. **Состояние прямо в Composable через `remember { mutableStateOf(...) }`** — теряется при поворотах экрана, нельзя тестировать в отрыве от UI.
2. **Набор отдельных `StateFlow`** в ViewModel (`val tracks`, `val isLoading`, `val error`) — UI собирает по частям, легко забыть один, появляются несогласованные состояния (есть треки и одновременно `isLoading = true`).
3. **`mutableStateOf` в ViewModel** — работает, но теряется «строгая» реактивность: `StateFlow` можно перекладывать между слоями, комбинировать, а `State<T>` — нет.

Правильно — один `StateFlow<UiState>`, где `UiState` описывает весь экран целиком, и `SharedFlow<Effect>` для одноразовых событий. Это называется **unidirectional data flow**: событие идёт в ViewModel через `onEvent(...)`, оттуда возвращается новый `UiState`.

---

## Что реализуем

Пишем **весь presentation-слой без экранов** (экраны — в главе 07, кроме одного, который сделаем здесь целиком для проверки).

Порядок строго последовательный: каждый файл использует только то, что уже написано выше.

```
shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/
├── common/
│   ├── Duration.kt              (1.5 — форматирование мс → "3:07")
│   ├── StateViews.kt            (1.6 — LoadingBox / ErrorBanner / EmptyState)
│   └── TrackRow.kt              (1.7 — строка трека в списке)
├── tracks/
│   ├── TracksUiState.kt         (1.1)
│   ├── TracksEvent.kt           (1.2)
│   ├── TracksEffect.kt          (1.3)
│   ├── TracksViewModel.kt       (1.4)
│   └── TracksScreen.kt          (1.8)
├── albums/
│   ├── AlbumsUiState.kt         (2.1)
│   └── AlbumsViewModel.kt       (2.2)
├── albumdetails/
│   ├── AlbumDetailsUiState.kt   (3.1)
│   ├── AlbumDetailsEvent.kt     (3.2)
│   └── AlbumDetailsViewModel.kt (3.3)
├── useralbums/
│   ├── UserAlbumsUiState.kt     (4.1)
│   ├── UserAlbumsEvent.kt       (4.2)
│   └── UserAlbumsViewModel.kt   (4.3)
└── player/
    ├── PlayerUiState.kt         (5.1)
    ├── PlayerEvent.kt           (5.2)
    └── PlayerViewModel.kt       (5.3)
```

Плюс два файла вне этого модуля — ими заканчивается часть 1:

```
shared/src/commonMain/kotlin/org/example/mp3player/shared/RootScreen.kt   (1.9  — заменяем тело)
composeApp/src/androidMain/kotlin/org/example/mp3player/MainActivity.kt   (1.10 — запрос разрешения)
```

Нумерация — это порядок написания. Фича «Треки» доводится до конца (от state до работающего экрана), только потом берёмся за «Альбомы», и так далее.

Что уже есть к этому моменту и чем мы пользуемся:

| Откуда | Что |
|---|---|
| `:core` (глава 04) | `AudioTrack`, `PlaybackState`, `RepeatMode`, `AudioPlayer` — пакет `org.example.mp3player.core.audio.player` |
| `:shared:domain` (главы 02, 03) | `Album`, `UserAlbum` — пакет `...domain.model`; `TracksRepository`, `AlbumsRepository`, `UserAlbumsRepository` — пакет `...domain.repository` |
| `:shared:presentation` (глава 05) | `presentationModule` — пока пустой |

---

## Часть 1 — фича «Треки»

### 1.1 — `TracksUiState`

`UiState` — снимок «что должно быть на экране прямо сейчас». Есть два способа его описать, и выбор между ними — главное архитектурное решение этой главы.

**Вариант А — один `data class` со всеми полями:**

```kotlin - пример (так мы НЕ делаем, показано для сравнения)
data class TracksUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
)
```

Проблема — **невозможные комбинации полей**. `isLoading = true` и одновременно `error != null` и `tracks` непустой: что рисовать? Компилятор такое пропускает, а на экране придётся писать лестницу из `when { state.isLoading && state.tracks.isEmpty() -> ...; state.error != null && ... -> ... }`. И ещё `state.error!!` — двойное отрицание, которое рано или поздно упадёт с NPE.

**Вариант Б — `sealed interface` с вариантами состояния:**

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/tracks/TracksUiState.kt
package org.example.mp3player.presentation.tracks

import org.example.mp3player.core.audio.player.AudioTrack

sealed interface TracksUiState {

    /** Первая загрузка: треков ещё нет, показываем спиннер на весь экран. */
    data object Loading : TracksUiState

    /** Загрузка провалилась и показать нечего. */
    data class Error(val errorText: String) : TracksUiState

    /** Есть что показывать. [isLoading] = идёт фоновое обновление поверх готового списка. */
    data class Content(
        val tracks: List<AudioTrack> = emptyList(),
        val searchQuery: String = "",
        val isLoading: Boolean = false,
    ) : TracksUiState {

        val filteredTracks: List<AudioTrack>
            get() = if (searchQuery.isBlank()) {
                tracks
            } else {
                tracks.filter { track ->
                    track.title.contains(searchQuery, ignoreCase = true) ||
                        track.artist.contains(searchQuery, ignoreCase = true)
                }
            }
    }
}
```

Мы выбираем **вариант Б**. Что это даёт:

- **Невозможные состояния не компилируются.** Нет `Error` с непустым списком треков, нет `Loading` с текстом ошибки. Типы гарантируют.
- **`when` на экране становится exhaustive.** Компилятор потребует обработать все три ветки; добавишь четвёртую — все `when` в проекте перестанут компилироваться, пока не допишешь ветку. Забыть невозможно.
- **`error!!` исчезает.** Внутри ветки `is TracksUiState.Error` поле `errorText` — обычный non-null `String`.

Цена, которую платим — её надо знать заранее:

- **Поля не переживают смену варианта.** Ушли в `Error` — потеряли `searchQuery`. Если это важно (например, поиск должен сохраняться при ошибке), либо дублируешь поле во всех вариантах, либо выносишь его в обёртку `data class ScreenState(val query: String, val content: TracksUiState)`. Для нашего экрана не важно: ошибка возникает только при первой загрузке, когда поиска ещё нет.
- **Smart cast не работает через `by`-делегат.** В Composable придётся писать `when (val s = state)` — разберём это в 1.8.

**`data object Loading`** — состояние без данных. `data` даёт человекочитаемый `toString()` (`"Loading"` вместо `"org.example...TracksUiState$Loading@4f2a1"`) и корректный `equals` — это важно, потому что `StateFlow` сравнивает значения через `equals` и не эмитит повторно одинаковые.

**`filteredTracks` — computed property.** `val ... get() = ...` — это **не поле**, а функция, замаскированная под свойство. На каждый доступ к `state.filteredTracks` тело `get` выполняется заново.

Альтернатива — хранить `filteredTracks` отдельным полем и держать его синхронным с `tracks` + `searchQuery`. Минус: где-то забыл пересчитать → state несогласован. С computed `get()` это невозможно — значение всегда выводится из других полей.

Цена: на каждый рендер `LazyColumn` Compose читает `state.filteredTracks` — фильтрация выполняется заново. На 5000 треков — несколько миллисекунд. Если профилировщик покажет, что медленно, мемоизируешь через `remember(state.tracks, state.searchQuery) { ... }` прямо в Composable. Обычно эта оптимизация не нужна.

### 1.2 — `TracksEvent`

`UiEvent` — что пользователь может сделать на экране. `sealed interface` с подтипом под каждое действие.

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

`sealed interface` — каждый экземпляр `TracksEvent` это один из перечисленных. В `when` компилятор проверит, что все ветки покрыты: забыл обработать новое событие → ошибка компиляции (если `when` используется как выражение или стоит в `when` без `else`).

`data object Load` — событие без полей (команда «выполни X»). `data class Search(val query: String)` — событие с данными.

`AddToUserAlbum` — единственное событие, у которого пока не будет источника: кнопку «добавить в мой альбом» мы повесим в главе 07 (Шаг 15), когда появятся экран «Мои альбомы» и диалоги. Обработчик пишем сейчас, потому что он часть той же ViewModel; UI подключится позже без единой правки здесь. Если тебе неуютно держать неиспользуемый код — можешь дописать это событие вместе с главой 07, ничего не сломается.

### 1.3 — `TracksEffect`

Отдельно от state — **одноразовые эффекты**: показать снэкбар, уйти на другой экран. Их нельзя класть в `UiState` (разберём почему в 1.4).

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/tracks/TracksEffect.kt
package org.example.mp3player.presentation.tracks

sealed interface TracksEffect {
    data object OpenPlayer : TracksEffect
    data class ShowMessage(val text: String) : TracksEffect
}
```

### 1.4 — `TracksViewModel`

Самая сложная часть паттерна. Ответственности: подписаться на источники данных, скомбинировать их в `UiState`, отдать `StateFlow` наружу, обработать события, слать эффекты.

Собираем файл по кускам. Сначала — пакет, импорты, объявление класса с DI через конструктор:

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/tracks/TracksViewModel.kt
package org.example.mp3player.presentation.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.example.mp3player.core.audio.player.AudioPlayer
import org.example.mp3player.domain.repository.TracksRepository
import org.example.mp3player.domain.repository.UserAlbumsRepository

class TracksViewModel(
    private val tracksRepository: TracksRepository,
    private val userAlbumsRepository: UserAlbumsRepository,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    // дальше — приватные источники state, публичный state через combine + stateIn,
    // _effects, onEvent-диспатчер, приватные обработчики
}
```

Зависимости приходят через конструктор — Koin из главы 05 соберёт их сам. ViewModel не знает про реализации, только про интерфейсы (`TracksRepository`, `UserAlbumsRepository`) и `AudioPlayer` из `:core`.

`ViewModel` из `androidx.lifecycle` — наследуемся, чтобы получить `viewModelScope`. Под капотом это `SupervisorJob + Dispatchers.Main.immediate`, который **отменяется** в `onCleared()` (когда ViewModel больше не нужна). Все корутины, запущенные в `viewModelScope.launch { ... }`, автоматически прерываются — никаких утечек.

Добавляем приватные источники state — то, чем сама ViewModel «крутит» (в отличие от `observeTracks()`, который приходит из репозитория):

```kotlin
class TracksViewModel(...) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _error = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)

    // дальше — публичный state (combine + stateIn), _effects, onEvent, обработчики
}
```

`MutableStateFlow` — контейнер с **ровно одним текущим значением** (см. подробнее в 02 Шаг 7). Подходит для UI-state: нам нужно «текущее значение поиска», а не «вся история изменений».

Дальше — главное: публичный `state: StateFlow<TracksUiState>` через `combine` + `stateIn`. Именно здесь четыре плоских источника превращаются в один из трёх вариантов sealed-состояния:

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
        when {
            // Ошибка имеет смысл, только когда показать вообще нечего.
            error != null && tracks.isEmpty() -> TracksUiState.Error(error)

            // Первая загрузка: треков ещё нет.
            loading && tracks.isEmpty() -> TracksUiState.Loading

            // Есть список (возможно, пустой после успешного скана) — рисуем контент.
            else -> TracksUiState.Content(
                tracks = tracks,
                searchQuery = query,
                isLoading = loading,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TracksUiState.Loading,
    )

    // дальше — _effects, onEvent-диспатчер, приватные обработчики
}
```

**Порядок веток в `when` — это и есть приоритет состояний.** Ошибка при уже загруженном списке (пользователь нажал «Обновить», и скан упал) не выкидывает пользователя на экран ошибки: список остаётся, а про ошибку сообщим снэкбаром. Это осознанное продуктовое решение, и оно выражено одной строкой `error != null && tracks.isEmpty()`.

**`combine(flowA, flowB, ...) { ... }` — timing.** Что физически делает `combine`:

1. Подписывается на все входные `Flow` параллельно.
2. **Ждёт первого эмита от каждого** из них. Пока хотя бы один молчит — `combine` не эмитит.
3. Как только все выдали хотя бы одно значение — лямбда вызывается с этими значениями, `combine` эмитит результат.
4. Дальше: каждый раз, когда **любой** из входов эмитит новое значение, `combine` пересчитывает результат с новейшими значениями всех входов.

В нашем случае все четыре источника — либо `MutableStateFlow` с initial-value, либо `Flow` от репозитория, который под капотом `_tracks.asStateFlow()` и тоже сразу эмитит. Поэтому первый эмит `combine` практически мгновенный.

Подвох: если один из источников — холодный `Flow`, который ничего не эмитит без триггера, `combine` будет тихо молчать. Не будет ошибки, не будет initial value — ничего. Симптом: `state` навсегда завис на `initialValue`. Решение — либо только `StateFlow`-источники, либо `flow.onStart { emit(...) }`.

**`stateIn` — превращает `Flow` в `StateFlow`.** Чтобы понять, что он делает, надо различать холодный и горячий потоки:

- **Холодный (`Flow`).** Аналогия: видеокассета. Пока никто не вставил кассету и не нажал play — ничего не происходит. Каждый, кто вставит свою — увидит запись с начала.
- **Горячий (`StateFlow`, `SharedFlow`).** Аналогия: радиоэфир. Эфир идёт всегда, независимо от слушателей. Кто включил приёмник — слышит то, что играет прямо сейчас.

После `stateIn` наш `combine` уже не cold-flow. Что физически делает `stateIn`:

1. Создаёт **одну upstream-корутину** в указанном `scope` — это «приёмник» исходного `Flow`.
2. Запускает (или нет — зависит от `started`) подписку на upstream.
3. Каждый эмит upstream'а кладёт во внутренний `MutableStateFlow.value`.
4. Возвращает наружу `StateFlow<T>` — read-only обёртку над этим внутренним state'ом.

Все downstream-подписчики (твой UI) подписываются не на исходный `Flow` напрямую, а на этот общий `StateFlow`. Это — **ключевая** оптимизация: один upstream обслуживает многих подписчиков.

**`SharingStarted` варианты:**

- **`Eagerly`** — upstream-корутина стартует сразу при `stateIn`, живёт всё время, пока живёт `scope`. Для критичных данных (плеер, настройки).
- **`Lazily`** — upstream стартует при первом подписчике, дальше живёт всё время `scope`.
- **`WhileSubscribed(stopTimeoutMillis)`** — upstream стартует при первом подписчике; когда последний отписался, upstream живёт ещё `stopTimeoutMillis` мс, потом отменяется.

Зачем `5_000` мс в `WhileSubscribed`: при повороте экрана Composable destroy → recreate занимает считанные мс. С `WhileSubscribed(0)` между двумя версиями экрана upstream успел бы умереть, и новый экран запустил бы его заново — лишняя работа. 5 секунд — типичный «зазор»: достаточный для recreate, недостаточный для «юзер вернулся через минуту».

`initialValue = TracksUiState.Loading` — значение, которое видит подписчик, **пока upstream ещё не выдал ничего**. Это может случиться многократно (например, после `WhileSubscribed`-cancel новый подписчик через 6 секунд — upstream стартует заново, и до первого эмита подписчик видит initial).

Теперь — `SharedFlow` для одноразовых эффектов:

```kotlin
class TracksViewModel(...) : ViewModel() {

    // ... _searchQuery / _error / _isLoading / state — выше ...

    private val _effects = MutableSharedFlow<TracksEffect>()
    val effects: SharedFlow<TracksEffect> = _effects.asSharedFlow()

    // дальше — onEvent-диспатчер и приватные обработчики
}
```

**`MutableStateFlow` vs `MutableSharedFlow` — фундаментальная разница.** В одной ViewModel используются оба:

| | `MutableStateFlow<T>` | `MutableSharedFlow<T>` |
|---|---|---|
| Хранит «текущее значение» | Да, всегда (`.value`) | Нет |
| Что получит новый подписчик | Текущее значение немедленно | Только то, что эмитится после подписки (если буфер 0) |
| Можно ли «потерять» эмит | Нет, но можно «conflate» (см. ниже) | Да, если буфер 0 и нет активных подписчиков |
| Аналогия | «Радио»: всегда что-то транслирует | «Чат»: пишешь сообщение, кто слушает — увидит |
| Для чего | UI-state (всегда нужен «текущий вид») | Эффекты (снэкбар, навигация) — событие происходит один раз |

Почему так разделено: типичная ловушка — положить «однократное событие» в `StateFlow`. Например, `val errorMessage: StateFlow<String?>`. Пользователь увидел снэкбар, повернул экран — composable пересоздался, подписался, получил **то же самое значение** «Ошибка» — снэкбар показывается заново. Бесконечный цикл.

Со `SharedFlow` без буфера такого не происходит: подписался **после** эмита — не увидел его. Это и нужно для эффектов.

`StateFlow` всегда **conflated**: если за время, пока подписчик обрабатывает значение, ты эмитишь несколько новых — он увидит только последнее. Промежуточные **не** дойдут. Для UI это нормально (нам важен последний снимок).

`MutableSharedFlow<TracksEffect>()` без аргументов — буфера нет. Если эмитить, пока никто не слушает, `emit` заблокируется (suspend) до подписчика. Безопасное поведение для эффектов: «ни одного сообщения не потеряем».

`extraBufferCapacity = 64` — вариант с буфером на 64 события. Если буфер полон, поведение задаётся `onBufferOverflow`: `SUSPEND` / `DROP_OLDEST` / `DROP_LATEST`.

`asSharedFlow()` — апкаст до read-only типа (как `asStateFlow()`).

Диспатчер событий — единственная точка входа для UI:

```kotlin
class TracksViewModel(...) : ViewModel() {

    // ... state, _effects — выше ...

    fun onEvent(event: TracksEvent) {
        when (event) {
            TracksEvent.Load -> load()
            TracksEvent.Refresh -> refresh()
            is TracksEvent.Search -> _searchQuery.value = event.query
            is TracksEvent.PlayTrack -> playTrack(event.index)
            is TracksEvent.AddToUserAlbum -> addToAlbum(event.trackId, event.albumId)
        }
    }

    // дальше — приватные обработчики load / refresh / playTrack / addToAlbum
}
```

`when` на `sealed interface` — компилятор проверит exhaustiveness. Добавил новое событие → ошибка компиляции, пока не добавишь ветку. Гарантирует, что все события обрабатываются.

Простой случай `Search` — синхронно обновляем `_searchQuery.value`. `combine` сразу пересчитает `state`.

И финал — приватные обработчики:

```kotlin
class TracksViewModel(...) : ViewModel() {

    // ... всё выше ...

    private fun load() {
        // Первая загрузка — делегируем репозиторию, observeTracks сам эмитнет результат.
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { tracksRepository.refresh() }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _error.value = e.message ?: "Не удалось обновить"
                }
            _isLoading.value = false
        }
    }

    private fun playTrack(index: Int) {
        val content = state.value as? TracksUiState.Content ?: return
        val queue = content.filteredTracks
        if (index !in queue.indices) return
        audioPlayer.play(queue, startIndex = index)
        viewModelScope.launch { _effects.emit(TracksEffect.OpenPlayer) }
    }

    private fun addToAlbum(trackId: String, albumId: Long) {
        viewModelScope.launch {
            runCatching { userAlbumsRepository.addTrack(albumId, trackId) }
                .onSuccess { _effects.emit(TracksEffect.ShowMessage("Добавлено")) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _effects.emit(TracksEffect.ShowMessage("Ошибка: ${e.message}"))
                }
        }
    }
}
```

`viewModelScope.launch` — корутина, которая отменится при `onCleared()`. Если экран закрылся в момент `refresh()` — корутина прервётся, ничего не утечёт. Оговорка: внутри цикла без suspend-точек (`while (true) { sum += 1 }`) корутина не сможет отмениться — cancellation проверяется только в suspend-точках. Всегда `delay`/`yield`/любой `suspend`-вызов внутри тяжёлых циклов.

**`runCatching { ... }` — ловушка с `CancellationException`.** Это Kotlin-сахар вокруг `try/catch`. Возвращает `Result<T>`, с которым удобно работать через `onSuccess`/`onFailure`.

Проблема в корутинах: `runCatching` ловит **все** `Throwable`, включая `CancellationException`. А `CancellationException` — особый сигнал: «отмени корутину». Он **должен** пробрасываться вверх, иначе корутина не остановится корректно.

Сценарий бага, если не пробрасывать:

1. Пользователь нажал «Обновить» → `viewModelScope.launch { runCatching { tracksRepository.refresh() } }`.
2. Через 100 мс ушёл с экрана → `viewModelScope` отменяется.
3. Cancellation идёт вниз: `refresh()` бросает `CancellationException`.
4. `runCatching` ловит его и кладёт в `Result.failure(CancellationException(...))`.
5. `onFailure` срабатывает → `_error.value = "JobCancellation: ..."`.
6. Пользователь возвращается на экран → видит непонятную ошибку.

Поэтому в коде выше **каждый** `onFailure` начинается со строки `if (e is CancellationException) throw e`. Это не украшательство, а обязательный шаблон. Альтернатива — явный `try { ... } catch (e: CancellationException) { throw e } catch (e: Exception) { ... }`.

**`state.value as? TracksUiState.Content ?: return` — идиома для sealed-состояния.** Тут проявляется цена варианта Б: у `TracksUiState` нет поля `filteredTracks`, оно есть только у `Content`.

- `as?` — safe cast: если объект нужного типа, вернёт его с этим типом; если нет — `null` (обычный `as` бросил бы `ClassCastException`).
- `?: return` — Elvis: если `null`, просто выходим из функции.

Читается как «если мы сейчас не в контенте — играть нечего, ничего не делаем». Такое действительно может случиться: пользователь тапнул по треку ровно в момент, когда список опустел и state ушёл в `Loading`.

### 1.5 — `common/Duration.kt`

Форматирование длительности нужно и в списке треков (1.7), и на экране плеера (5.1). Пишем один раз в `common`.

Есть три способа, и два из них ломаются:

```kotlin - пример (что НЕ работает в commonMain)
// 1. String.format — только JVM. В commonMain "Unresolved reference: format".
return "%d:%02d".format(minutes, seconds)

// 2. kotlinx.datetime.LocalTime — компилируется, но падает на длинных треках.
return timeFormatter.format(LocalTime(0, minutes.toInt(), seconds.toInt()))
```

Первый вариант — `String.format` живёт в `kotlin.text` только для JVM-таргета. В `commonMain` KMP-модуля его нет, файл не соберётся.

Второй вариант хуже: он **компилируется**, но `LocalTime(hour, minute, second)` требует `minute in 0..59`. Трек длиной 1 час 5 минут → `minutes = 65` → `IllegalArgumentException` в рантайме. Плюс `LocalTime` — это «время суток», а не «длительность»: семантически неверный тип для задачи. Тащить ради этого зависимость `kotlinx-datetime` не нужно.

Третий вариант — руками, на чистом Kotlin, работает везде:

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/common/Duration.kt
package org.example.mp3player.presentation.common

/**
 * Миллисекунды → "3:07" или "1:05:42" для треков длиннее часа.
 * Чистый Kotlin: работает и на Android, и на iOS, без зависимостей.
 */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "$hours:${minutes.pad2()}:${seconds.pad2()}"
    } else {
        "$minutes:${seconds.pad2()}"
    }
}

private fun Long.pad2(): String = toString().padStart(2, '0')
```

`padStart(2, '0')` — extension из `kotlin.text` (доступна на всех таргетах): дополняет строку слева до нужной длины. `7` → `"07"`, `42` → `"42"` (длиннее не трогает).

`coerceAtLeast(0)` — `MediaController.currentPosition` до старта воспроизведения может вернуть `C.TIME_UNSET` (= `Long.MIN_VALUE`). Без clamp получили бы `"-9223372036854:-08"`.

Почему `if (hours > 0)`, а не всегда `0:03:07` — для трека 3 минуты ведущий «0:» это визуальный мусор. Все плееры показывают часы только когда они есть.

### 1.6 — `common/StateViews.kt`

Три состояния экрана (загрузка / ошибка / пусто) выглядят одинаково на всех экранах приложения. Пишем один раз.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/common/StateViews.kt
package org.example.mp3player.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Спиннер по центру свободного места. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** Ошибка + кнопка «повторить». */
@Composable
fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    retryText: String = "Попробовать ещё раз",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(retryText)
        }
    }
}

/** Пустой экран с пояснением. [description] опционально. */
@Composable
fun EmptyState(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
```

`modifier: Modifier = Modifier` последним параметром со значением по умолчанию — **конвенция Compose**. Она позволяет вызывающему коду настроить размер/отступы компонента снаружи, не меняя его внутренности. Всегда добавляй её к переиспользуемым Composable.

`description: String? = null` — один компонент закрывает оба случая: «Нет музыки» и «Нет музыки» + «Добавь треки на устройство и обнови». Если бы сделали два разных компонента с разными сигнатурами, дальше начались бы ошибки вида «а какой из них я вызываю».

Строки пока захардкожены по-русски. В главе 07 заменим их на `stringResource(...)` — так работает и локализация, и порядок изложения: сначала работающий экран, потом локализация.

### 1.7 — `common/TrackRow.kt`

Строка трека — одинаковая в списке всех треков и в деталях альбома.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/common/TrackRow.kt
package org.example.mp3player.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.example.mp3player.core.audio.player.AudioTrack

@Composable
fun TrackRow(
    track: AudioTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = trailingContent ?: {
            Text(formatDuration(track.duration))
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
```

`ListItem` из Material 3 — готовый layout строки списка со слотами `headlineContent` / `supportingContent` / `leadingContent` / `trailingContent` и правильными отступами по гайдлайнам. Писать `Row` руками не нужно.

`Modifier.clickable(onClick = onClick)` — именно так, а не `Card(onClick = ...)`: у `ListItem` нет параметра `onClick`, кликабельность добавляется модификатором. Он же даёт ripple-эффект и обработку accessibility.

`trailingContent: @Composable (() -> Unit)? = null` — **слот с дефолтом**. По умолчанию справа длительность трека; но если вызывающему нужна кнопка «добавить в альбом» — он передаст свою лямбду. Это стандартный приём переиспользования компонентов: не плодить `TrackRowWithButton`, а дать слот.

`overflow = TextOverflow.Ellipsis` вместе с `maxLines = 1` — длинное название обрежется многоточием, а не уедет за край.

`formatDuration(track.duration)` — та самая функция из 1.5. Поле в `AudioTrack` называется `duration` и хранит миллисекунды.

### 1.8 — `TracksScreen`

Теперь всё, что нужно экрану, уже написано. Собираем.

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
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TracksScreen(
    onOpenPlayer: () -> Unit,
    onSnackbar: (String) -> Unit,
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
        TracksUiState.Loading -> LoadingBox()

        is TracksUiState.Error -> ErrorBanner(
            message = current.errorText,
            onRetry = { viewModel.onEvent(TracksEvent.Refresh) },
        )

        is TracksUiState.Content -> TracksContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun TracksContent(
    state: TracksUiState.Content,
    onEvent: (TracksEvent) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onEvent(TracksEvent.Search(it)) },
            placeholder = { Text("Поиск") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        val tracks = state.filteredTracks
        when {
            state.tracks.isEmpty() -> EmptyState(
                title = "Нет музыки",
                description = "Добавь треки на устройство и обнови",
            )

            tracks.isEmpty() -> EmptyState(title = "Ничего не найдено")

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

Разбираем ключевые места.

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

**Вывод:** для UI всегда `collectAsStateWithLifecycle`.

`by` — property delegation: `state` ведёт себя как `TracksUiState`, под капотом каждое чтение идёт в `MutableState.value` (см. подробнее в 02 Шаг 10).

**`when (val current = state)` — почему нельзя просто `when (state)`.**

Это прямое следствие `by`-делегата. Smart cast работает только для того, что компилятор может гарантированно перечитать одинаково: локальные `val`, приватные `val`-поля. А `state` — **делегированное свойство**: каждое обращение к нему это вызов `getValue()`, и компилятор обязан предполагать, что второе обращение может вернуть другое значение (state ведь реально меняется между рекомпозициями).

Поэтому:

```kotlin - пример (не компилируется)
when (state) {
    is TracksUiState.Error -> Text(state.errorText)   // ❌ Smart cast to 'Error' is impossible
}
```

`when (val current = state)` читает делегат **один раз**, кладёт в локальный `val`, и вот по нему smart cast уже работает: внутри ветки `is TracksUiState.Error` у `current` тип `TracksUiState.Error`, поле `errorText` доступно напрямую. Никаких `!!`.

Эта же ошибка ловит на `state.error!!` при плоском data-class-состоянии — там `!!` компилируется, но может упасть. Здесь у компилятора нет варианта «пропустить»: он заставляет написать правильно.

**`LaunchedEffect(Unit) { ... }` — что физически.**

`LaunchedEffect(key1) { блок }` — composable-функция, которая:
1. При первом появлении в composition запускает корутину в специальном scope, выполняя `блок`.
2. Если `key1` изменился между рекомпозициями — текущая корутина **отменяется**, и запускается новая.
3. Если Composable уходит из composition — корутина отменяется.

**`Unit` как ключ** — стабильное значение, которое никогда не меняется → корутина запускается ровно **один раз**, при появлении Composable.

Типичная ловушка: написать `LaunchedEffect(state)`, где `state` — твой `UiState`. Каждое изменение state создаёт новый объект → ключ меняется → корутина перезапускается на каждый эмит. Симптом: «мой эффект почему-то выполняется несколько раз».

Правило: **ключ должен меняться ровно тогда, когда ты хочешь перезапустить эффект**.

Два `LaunchedEffect(Unit)` в одном Composable — это нормально, они независимы:
- Первый стартует первоначальную загрузку.
- Второй подписывается на `effects`.

**`collect`, а не `collectLatest` для эффектов.**

`collect { блок }`:
- Получает значение → выполняет блок → ждёт следующего значения.
- Если в блоке стоит `delay(1000)` и за это время пришли 5 эмитов — все 5 будут обработаны последовательно.

`collectLatest { блок }`:
- Получает значение → запускает блок в новой корутине.
- Если пришёл новый эмит, **пока блок ещё работает** — текущий блок отменяется, новый запускается с нуля.

Для эффектов нужен именно `collect`: мы хотим показать **все** сообщения по порядку. С `collectLatest` показ снэкбара «Добавлено» отменился бы, если бы следом прилетел ещё один эффект. Пользователь бы не увидел подтверждения.

Где `collectLatest` действительно нужен — дебаунс и отменяемая загрузка:

```kotlin - пример (иллюстрация, писать не надо)
searchQuery.collectLatest { query ->
    delay(300)            // пользователь продолжает печатать → отмена, delay не досчитал
    val results = api.search(query)
    _results.value = results
}
```

**Разделение `TracksScreen` / `TracksContent`.** Публичная функция занимается «внешним миром»: подписки, эффекты, выбор ветки состояния. Приватная `TracksContent` получает **уже суженный тип** `TracksUiState.Content` и `(TracksEvent) -> Unit` — она ничего не знает про ViewModel и Koin. Такую функцию легко положить в `@Preview` и протестировать.

`viewModel::onEvent` — **method reference**. Эквивалент `{ event -> viewModel.onEvent(event) }`, но короче и стабильнее для Compose (одна и та же ссылка между рекомпозициями, а не новая лямбда каждый раз).

**`key = { _, track -> track.id }` в `itemsIndexed`.** `key` — функция, возвращающая стабильный идентификатор элемента. Без ключа Compose сравнивает items по позиции: вставил элемент в начало → Compose думает, что изменились **все** → scroll и анимации сбрасываются. С ключом: «элемент с id=5 переехал с позиции 0 на 1, но это тот же элемент» — state сохраняется. Правило: всегда `key` в `LazyColumn` / `LazyVerticalGrid`.

`itemsIndexed` (а не `items`) — потому что `TracksEvent.PlayTrack` принимает `index`: ViewModel сама достанет трек из очереди. UI просто передаёт позицию, не зная про логику. Подчёркивание `_` в лямбде ключа — «этот параметр (index) мне не нужен».

**Два разных «пусто».** `state.tracks.isEmpty()` — на устройстве вообще нет музыки. `tracks.isEmpty()` (после фильтра) — музыка есть, но поиск ничего не нашёл. Это разные ситуации и разные тексты; сообщение «Нет музыки» при активном поиске сбивало бы с толку.

### 1.9 — Регистрируем в Koin и проверяем

Открываем `PresentationModule.kt` (из главы 05) и добавляем первую ViewModel:

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/di/PresentationModule.kt
package org.example.mp3player.presentation.di

import org.example.mp3player.presentation.tracks.TracksViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::TracksViewModel)
    // остальные добавим по мере написания в частях 2–5
}
```

Без этого `koinViewModel()` на экране упадёт в рантайме с `NoDefinitionFoundException`. **Возвращайся в этот файл после каждой новой ViewModel.**

Чтобы увидеть результат прямо сейчас, временно подключим экран в корневой Composable.

> **Файл `RootScreen.kt` уже есть** — он приехал из шаблона KMP-проекта и показывает демо-`Screen()` с кнопкой «Click me!». Мы **заменяем его тело**, а не создаём файл заново. Заодно можно удалить оставшийся от шаблона `shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/Screen.kt` — он больше не нужен и только мешает искать по проекту.

```kotlin
// shared/src/commonMain/kotlin/org/example/mp3player/shared/RootScreen.kt
package org.example.mp3player.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.example.mp3player.presentation.tracks.TracksScreen

@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    MaterialTheme {
        // Временно, до главы 07: навигации и снэкбара ещё нет.
        TracksScreen(
            onOpenPlayer = {},
            onSnackbar = {},
        )
    }
}
```

Запускать пока рано: приложение ни разу не спросило разрешение на чтение аудио, поэтому `MediaStore` вернёт пустой список. Дописываем последний кусок — шаг 1.10.

### 1.10 — Подключаем запрос разрешения

В главе 02 (Шаг 10) мы написали `rememberAudioPermissionState()` в `composeApp/src/androidMain/.../permissions/AudioPermission.kt` — и **ни разу его не вызвали**. Сейчас исправляем: без этого шага список треков будет пустым на любом устройстве.

Почему связка живёт в `MainActivity`, а не в `TracksScreen`: `rememberAudioPermissionState()` использует `android.Manifest` и `ContextCompat` — Android-only API. `TracksScreen` лежит в `commonMain` модуля `:shared:presentation`, который собирается ещё и под iOS; Android-код туда не положить. Точка входа `MainActivity` — Android-only по определению, там этому месту и быть.

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/MainActivity.kt
package org.example.mp3player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.mp3player.permissions.AudioPermissionState
import org.example.mp3player.permissions.rememberAudioPermissionState
import org.example.mp3player.shared.RootScreen

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

/**
 * Пускает [content] дальше только когда разрешение на чтение аудио выдано.
 * Пока не выдано — просит его (один раз) и показывает баннер с кнопкой.
 */
@Composable
private fun AudioPermissionGate(content: @Composable () -> Unit) {
    val (permissionState, requestPermission) = rememberAudioPermissionState()

    LaunchedEffect(permissionState) {
        if (permissionState == AudioPermissionState.Unknown) {
            requestPermission()
        }
    }

    when (permissionState) {
        AudioPermissionState.Granted -> content()

        else -> MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Приложению нужен доступ к музыке на устройстве",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = requestPermission,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Разрешить")
                }
            }
        }
    }
}
```

**`AudioPermissionGate` — обёртка со слотом `content: @Composable () -> Unit`.** Тот же приём, что у `trailingContent` в `TrackRow` (1.7): вместо флага «показывать/не показывать» компонент принимает **кусок UI** и сам решает, звать его или нет. Плюс: `RootScreen()` вообще ничего не знает про разрешения — его можно переиспользовать на iOS как есть.

**`LaunchedEffect(permissionState)` — ключ не `Unit`.** Мы хотим спросить разрешение один раз при старте (`Unknown`), но **не** долбить пользователя диалогом после отказа (`Denied`) — иначе он застрянет в цикле. Проверка `== Unknown` внутри и ключ `permissionState` снаружи дают ровно это: эффект перезапускается при каждой смене статуса, но запрос уходит только из `Unknown`.

**`else ->` вместо перечисления `Denied` и `Unknown`.** Оба состояния рисуют одно и то же, а `when` тут — не выражение над `sealed`, exhaustiveness не требуется. Если позже понадобится разный текст («Отказано, включи в настройках» vs «Разреши доступ») — раскроешь `else` в две ветки.

Отдельный `MaterialTheme { }` вокруг баннера нужен, потому что тема живёт внутри `RootScreen`, а до неё мы в этой ветке не доходим — без обёртки `MaterialTheme.typography` упадёт на дефолтные значения.

Теперь запускай. Должен появиться системный диалог разрешения, а после «Разрешить» — список твоих треков с поиском. Тап по треку запустит воспроизведение (навигации на плеер пока нет — плеер играет в фоне, проверь по уведомлению).

Если список пустой при выданном разрешении — смотри Logcat по фильтру `Koin` (не собрался граф) и по `Scanner` (не нашёлся ни один трек — см. подводный камень №1 в главе 02).

---

## Часть 2 — фича «Альбомы»

### 2.1 — `AlbumsUiState`

Экран альбомов проще: данных из репозитория берём один поток, ошибок у него нет (альбомы выводятся из уже загруженных треков — см. `AlbumsRepositoryImpl` в главе 02), событий пользователь не шлёт.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/albums/AlbumsUiState.kt
package org.example.mp3player.presentation.albums

import org.example.mp3player.domain.model.Album

sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data object Empty : AlbumsUiState
    data class Content(val albums: List<Album>) : AlbumsUiState
}
```

Обрати внимание: набор вариантов **разный** у разных экранов. Не надо тащить `Error` туда, где ошибка невозможна — это лишняя ветка `when`, которую всегда придётся заполнять заглушкой. Sealed-состояние описывает конкретный экран, а не абстрактный «экран вообще».

`Empty` отдельным вариантом (а не `Content(emptyList())`) — потому что UI для них принципиально разный: сетка карточек против текста по центру.

### 2.2 — `AlbumsViewModel`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/albums/AlbumsViewModel.kt
package org.example.mp3player.presentation.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.example.mp3player.domain.repository.AlbumsRepository

class AlbumsViewModel(
    albumsRepository: AlbumsRepository,
) : ViewModel() {

    val state: StateFlow<AlbumsUiState> = albumsRepository.observeAlbums()
        .map { albums ->
            if (albums.isEmpty()) AlbumsUiState.Empty else AlbumsUiState.Content(albums)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlbumsUiState.Loading,
        )
}
```

Вся ViewModel — семь строк. Так и должно быть: если данные уже реактивные, задача ViewModel сводится к «замапь доменную модель в UI-состояние».

`albumsRepository: AlbumsRepository` — **без `private val`**. Параметр используется только в инициализаторе `state`, хранить его в поле незачем. Kotlin позволяет обычный параметр конструктора, и Koin с ним работает точно так же.

В цепочке `.map { ... }` это **`Flow.map`** (импорт `kotlinx.coroutines.flow.map`), не `List.map`. Принимает лямбду `(T) -> R` и возвращает новый `Flow<R>`, применяя её к каждому upstream-эмиту. Сравнение с `List.map` подробно — в `02-PERMISSIONS_AND_SCAN.md`, Шаг 8.

Лайфхак: если IDE предлагает auto-import для `.map`, проверь, что подтянулся именно `kotlinx.coroutines.flow.map`. Импорт `kotlin.collections.map` в этом месте даст непонятную ошибку типов.

Добавляем в Koin:

```kotlin
// PresentationModule.kt
val presentationModule = module {
    viewModelOf(::TracksViewModel)
    viewModelOf(::AlbumsViewModel)
}
```

(и `import org.example.mp3player.presentation.albums.AlbumsViewModel`)

Экран `AlbumsScreen` напишем в главе 07 — там же, где появится сетка с обложками и навигация в детали альбома.

---

## Часть 3 — фича «Детали альбома»

### 3.1 — `AlbumDetailsUiState`

Первый экран, который зависит от **аргумента навигации** — `albumId`. Это меняет устройство ViewModel.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/albumdetails/AlbumDetailsUiState.kt
package org.example.mp3player.presentation.albumdetails

import org.example.mp3player.core.audio.player.AudioTrack
import org.example.mp3player.domain.model.Album

sealed interface AlbumDetailsUiState {
    data object Loading : AlbumDetailsUiState
    data class Error(val errorText: String) : AlbumDetailsUiState
    data class Content(
        val album: Album,
        val tracks: List<AudioTrack>,
    ) : AlbumDetailsUiState
}
```

`Error` здесь возможен — например, пользователь пришёл по deep link на альбом, которого уже нет на устройстве.

### 3.2 — `AlbumDetailsEvent` и эффекты

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/albumdetails/AlbumDetailsEvent.kt
package org.example.mp3player.presentation.albumdetails

sealed interface AlbumDetailsEvent {
    data object PlayAll : AlbumDetailsEvent
    data class PlayTrack(val index: Int) : AlbumDetailsEvent
}

sealed interface AlbumDetailsEffect {
    data object OpenPlayer : AlbumDetailsEffect
}
```

Два sealed-интерфейса в одном файле — нормально, когда они маленькие и всегда используются вместе. У `TracksEvent`/`TracksEffect` мы разнесли их по файлам, потому что там оба длиннее; жёсткого правила нет, ориентируйся на читаемость.

### 3.3 — `AlbumDetailsViewModel`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/albumdetails/AlbumDetailsViewModel.kt
package org.example.mp3player.presentation.albumdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.example.mp3player.core.audio.player.AudioPlayer
import org.example.mp3player.domain.repository.AlbumsRepository

class AlbumDetailsViewModel(
    private val albumId: String,
    albumsRepository: AlbumsRepository,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    private val _effects = MutableSharedFlow<AlbumDetailsEffect>()
    val effects: SharedFlow<AlbumDetailsEffect> = _effects.asSharedFlow()

    val state: StateFlow<AlbumDetailsUiState> = combine(
        albumsRepository.observeAlbums(),
        albumsRepository.observeTracksOfAlbum(albumId),
    ) { albums, tracks ->
        val album = albums.firstOrNull { it.id == albumId }
        if (album == null) {
            AlbumDetailsUiState.Error("Альбом не найден")
        } else {
            AlbumDetailsUiState.Content(album = album, tracks = tracks)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumDetailsUiState.Loading,
    )

    fun onEvent(event: AlbumDetailsEvent) {
        when (event) {
            AlbumDetailsEvent.PlayAll -> play(0)
            is AlbumDetailsEvent.PlayTrack -> play(event.index)
        }
    }

    private fun play(index: Int) {
        val content = state.value as? AlbumDetailsUiState.Content ?: return
        if (index !in content.tracks.indices) return
        audioPlayer.play(content.tracks, startIndex = index)
        viewModelScope.launch { _effects.emit(AlbumDetailsEffect.OpenPlayer) }
    }
}
```

**`private val albumId: String` первым параметром конструктора.** Это и есть аргумент навигации. Koin не может достать его из графа зависимостей — такого «типа String» в графе нет и быть не должно. Значит, его надо передать **в момент создания ViewModel**.

Регистрация в Koin отличается от предыдущих:

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/di/PresentationModule.kt
package org.example.mp3player.presentation.di

import org.example.mp3player.presentation.albumdetails.AlbumDetailsViewModel
import org.example.mp3player.presentation.albums.AlbumsViewModel
import org.example.mp3player.presentation.tracks.TracksViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::TracksViewModel)
    viewModelOf(::AlbumsViewModel)

    // albumId приходит снаружи, остальное Koin достаёт сам через get()
    viewModel { (albumId: String) -> AlbumDetailsViewModel(albumId, get(), get()) }
}
```

`viewModel { (albumId: String) -> ... }` — форма с **destructured parameters**. Скобки вокруг `albumId: String` — это деструктуризация объекта `ParametersHolder`, который Koin передаёт в лямбду. Читается как «первый переданный параметр, тип `String`».

`viewModelOf(::X)` для такой ViewModel не подойдёт: он умеет только «все параметры через `get()`».

На экране (напишем его в главе 07) это будет выглядеть так:

```kotlin - пример (превью из главы 07, сейчас не писать)
@Composable
fun AlbumDetailsScreen(
    albumId: String,
    onBack: () -> Unit,
    viewModel: AlbumDetailsViewModel = koinViewModel { parametersOf(albumId) },
) { /* ... */ }
```

`parametersOf(albumId)` (импорт `org.koin.core.parameter.parametersOf`) упаковывает аргументы в тот самый `ParametersHolder`. Порядок аргументов должен совпадать с порядком в лямбде модуля.

Kotlin разрешает `koinViewModel { parametersOf(albumId) }` в значении по умолчанию для параметра, потому что `albumId` объявлен **раньше** в том же списке параметров.

**Альтернатива — `SavedStateHandle`.** В Android-мире аргументы навигации часто достают так: `class VM(handle: SavedStateHandle)` и внутри `handle.toRoute<AlbumDetailsRoute>()`. Плюс — переживает kill процесса. Минус для нас — жёстко связывает ViewModel с navigation-библиотекой и усложняет тесты. Для учебного проекта явный параметр честнее: видно, что нужно классу, и в тесте создаёшь `AlbumDetailsViewModel("42", fakeRepo, fakePlayer)` без Koin и без навигации.

---

## Часть 4 — фича «Мои альбомы»

### 4.1 — `UserAlbumsUiState`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/useralbums/UserAlbumsUiState.kt
package org.example.mp3player.presentation.useralbums

import org.example.mp3player.domain.model.UserAlbum

sealed interface UserAlbumsUiState {
    data object Loading : UserAlbumsUiState
    data object Empty : UserAlbumsUiState
    data class Content(val albums: List<UserAlbum>) : UserAlbumsUiState
}
```

### 4.2 — `UserAlbumsEvent`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/useralbums/UserAlbumsEvent.kt
package org.example.mp3player.presentation.useralbums

sealed interface UserAlbumsEvent {
    data class Create(val title: String) : UserAlbumsEvent
    data class Delete(val id: Long) : UserAlbumsEvent
}

sealed interface UserAlbumsEffect {
    data class ShowMessage(val text: String) : UserAlbumsEffect
}
```

### 4.3 — `UserAlbumsViewModel`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/useralbums/UserAlbumsViewModel.kt
package org.example.mp3player.presentation.useralbums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.example.mp3player.domain.repository.UserAlbumsRepository

class UserAlbumsViewModel(
    private val repository: UserAlbumsRepository,
) : ViewModel() {

    val state: StateFlow<UserAlbumsUiState> = repository.observeAll()
        .map { albums ->
            if (albums.isEmpty()) UserAlbumsUiState.Empty else UserAlbumsUiState.Content(albums)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserAlbumsUiState.Loading,
        )

    private val _effects = MutableSharedFlow<UserAlbumsEffect>()
    val effects: SharedFlow<UserAlbumsEffect> = _effects.asSharedFlow()

    fun onEvent(event: UserAlbumsEvent) {
        when (event) {
            is UserAlbumsEvent.Create -> create(event.title)
            is UserAlbumsEvent.Delete -> delete(event.id)
        }
    }

    private fun create(title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.create(title = trimmed, description = "", coverUri = null)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _effects.emit(UserAlbumsEffect.ShowMessage("Не удалось создать: ${e.message}"))
            }
        }
    }

    private fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _effects.emit(UserAlbumsEffect.ShowMessage("Не удалось удалить: ${e.message}"))
                }
        }
    }
}
```

`if (trimmed.isBlank()) return` — **валидация живёт в ViewModel**, а не в диалоге. Диалог в главе 07 тоже будет блокировать кнопку при пустом вводе, но это UX-подсказка, а не гарантия. Гарантия — здесь: любой вызывающий, включая тест и будущий deep link, не создаст альбом без названия.

Здесь нет `_isLoading`: создание альбома в Room занимает единицы миллисекунд, показывать спиннер не за чем. Не добавляй состояния «на всякий случай» — каждое из них потом надо обрабатывать во всех `when`.

Добавляем в Koin:

```kotlin
// PresentationModule.kt
viewModelOf(::UserAlbumsViewModel)
```

---

## Часть 5 — фича «Плеер»

### 5.1 — `PlayerUiState`

Здесь источник один и он уже готов: `audioPlayer.state: StateFlow<PlaybackState>` из главы 04. ViewModel только мапит доменное состояние в UI-состояние.

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/player/PlayerUiState.kt
package org.example.mp3player.presentation.player

import org.example.mp3player.presentation.common.formatDuration

data class PlayerUiState(
    val title: String = "",
    val artist: String = "",
    val coverUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val progress: Float = 0f,
    val hasTrack: Boolean = false,
) {
    val positionText: String get() = formatDuration(positionMs)
    val durationText: String get() = formatDuration(durationMs)
}
```

**Почему тут `data class`, а не `sealed interface`.** У экрана плеера нет принципиально разных режимов отображения: нет загрузки (плеер либо играет, либо стоит на паузе), нет ошибки (ошибки воспроизведения мы бы показывали снэкбаром). Есть один макет, у которого меняются значения. Sealed-иерархия из одного варианта — бессмысленная церемония.

Это и есть правило выбора: **разные макеты → sealed; один макет с разными значениями → data class.** Не применяй sealed везде подряд только потому, что мы так сделали для треков.

`hasTrack: Boolean` — единственное «состояние»: показывать ли элементы управления или заглушку «ничего не играет». Одного флага хватает, отдельный вариант заводить не за чем.

`positionText` / `durationText` — computed properties поверх `formatDuration` из 1.5. UI-слой не должен уметь форматировать миллисекунды, он просто читает готовую строку.

### 5.2 — `PlayerEvent`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/player/PlayerEvent.kt
package org.example.mp3player.presentation.player

sealed interface PlayerEvent {
    data object PlayPause : PlayerEvent
    data object Next : PlayerEvent
    data object Previous : PlayerEvent
    data class SeekTo(val positionMs: Long) : PlayerEvent
    data class SeekToFraction(val fraction: Float) : PlayerEvent
}
```

### 5.3 — `PlayerViewModel`

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/player/PlayerViewModel.kt
package org.example.mp3player.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.example.mp3player.core.audio.player.AudioPlayer
import org.example.mp3player.core.audio.player.PlaybackState

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
                if (duration > 0) {
                    audioPlayer.seekTo((duration * event.fraction).toLong())
                }
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
            hasTrack = track != null,
        )
    }
}
```

`private fun PlaybackState.toUi()` — приватная extension-функция **внутри класса**. Receiver — `PlaybackState`, поэтому внутри тела `currentTrack`, `isPlaying`, `positionMs` доступны без префикса: это поля receiver'а. Одновременно виден и `this` самого ViewModel (если бы понадобился). Идиоматичный способ описать маппер, не засоряя публичный API.

`track?.title.orEmpty()` — цепочка из двух вещей: `?.title` даёт `String?`, `orEmpty()` превращает `null` в `""`. Компактнее, чем `track?.title ?: ""`.

`progress.coerceIn(0f, 1f)` — зажимаем в диапазон, который принимает `Slider` в Material 3. Без зажима при рассинхроне `positionMs`/`durationMs` (полинг позиции идёт раз в 500 мс, см. главу 04) можно получить `1.0001f` и краш слайдера.

`SeekToFraction` — пример события «преобразовать UI-единицу во внутреннюю». Слайдер оперирует долей `0f..1f`, плеер — миллисекундами. Пересчёт — задача ViewModel, а не Composable: так его можно протестировать.

Финальный `PresentationModule.kt` — все пять ViewModel:

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/di/PresentationModule.kt
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
    viewModel { (albumId: String) -> AlbumDetailsViewModel(albumId, get(), get()) }
}
```

---

## Чек-лист: что должно быть к концу главы

Написано и компилируется:

- [ ] `common/Duration.kt`, `common/StateViews.kt`, `common/TrackRow.kt`
- [ ] `tracks/` — `TracksUiState`, `TracksEvent`, `TracksEffect`, `TracksViewModel`, `TracksScreen`
- [ ] `albums/` — `AlbumsUiState`, `AlbumsViewModel`
- [ ] `albumdetails/` — `AlbumDetailsUiState`, `AlbumDetailsEvent`, `AlbumDetailsViewModel`
- [ ] `useralbums/` — `UserAlbumsUiState`, `UserAlbumsEvent`, `UserAlbumsViewModel`
- [ ] `player/` — `PlayerUiState`, `PlayerEvent`, `PlayerViewModel`
- [ ] `di/PresentationModule.kt` — все пять регистраций
- [ ] `RootScreen.kt` — временно показывает `TracksScreen`
- [ ] `MainActivity.kt` — `AudioPermissionGate` вокруг `RootScreen()`

Работает при запуске:

- [ ] При первом запуске появляется системный диалог разрешения на доступ к музыке.
- [ ] Виден список треков с устройства.
- [ ] Поиск фильтрует список по названию и исполнителю.
- [ ] Тап по треку запускает воспроизведение.
- [ ] При пустой медиатеке видно «Нет музыки», при пустом поиске — «Ничего не найдено».

НЕ написано (и это правильно — появится в главе 07):

- `AlbumsScreen`, `AlbumDetailsScreen`, `UserAlbumsScreen`, `PlayerScreen`
- Навигация, нижнее меню, снэкбар
- Локализация (строки пока захардкожены)

---

## Подводные камни

### 1. `when (state)` вместо `when (val current = state)`
Smart cast не работает через `by`-делегат. Симптом: «Smart cast to 'Content' is impossible, because 'state' is a property that has open or custom getter». Решение — присвоить в локальный `val` прямо в `when`.

### 2. `runCatching` без проброса `CancellationException`
Ловит сигнал отмены корутины и превращает его в «ошибку» на экране. Всегда `if (e is CancellationException) throw e` первой строкой в `onFailure`.

### 3. `stateIn` с `SharingStarted.Eagerly`
Используй только для критичных данных (плеер, настройки). Для обычных экранов `WhileSubscribed(5_000)` экономит батарею и CPU.

### 4. `collectLatest` для эффектов
Отменит показ предыдущего снэкбара, если эффекты идут подряд. Для эффектов — `collect`. `collectLatest` нужен для дебаунса и отменяемой загрузки.

### 5. `mutableStateOf` в ViewModel
Работает, но не комбинируется через `combine`, не тестируется как Flow, не переносится в KMP-логику. Держись `StateFlow`.

### 6. Блокирующий код в `onEvent`
`onEvent` не `suspend`. Нужна асинхронность — `viewModelScope.launch { }`. Никогда `Thread.sleep` или `runBlocking`.

### 7. Sealed-состояние ради sealed-состояния
Если у экрана один макет и меняются только значения (как у плеера) — `data class` честнее. Sealed нужен там, где макеты **взаимоисключающие**.

### 8. Забыл дописать `viewModelOf` в `PresentationModule`
Экран падает в рантайме: `NoDefinitionFoundException: No definition found for class 'AlbumsViewModel'`. Компилятор про это не знает — проверяется только запуском.

### 9. `repository.observeFoo()` напрямую в Composable
```kotlin
val tracks by tracksRepository.observeTracks().collectAsState(emptyList())  // ❌
```
UI напрямую зависит от data-слоя. Тест UI теперь требует `TracksRepository`. Всегда через ViewModel.

### 10. `LocalTime` для форматирования длительности
`LocalTime(0, minutes, seconds)` бросает `IllegalArgumentException` при `minutes >= 60`. Длительность — это не время суток. Используй `formatDuration` из 1.5.

---

## Try yourself

1. **Добавь сортировку**: `TracksEvent.SetSortOrder(SortOrder)`, поле `sortOrder` в `TracksUiState.Content`, сортировка внутри computed `filteredTracks`.

2. **Сохрани поиск при ошибке**: сейчас переход в `TracksUiState.Error` теряет `searchQuery`. Добавь поле `searchQuery` в `Error` и прокинь его в `combine`. Подумай, стоит ли оно того.

3. **Unit-тест `TracksViewModel`**: fake `TracksRepository`, возвращающий `flowOf(listOf(track1, track2))`. После `onEvent(Search("query"))` проверь, что `state.value` это `Content` с ожидаемым `filteredTracks`. Никакого Koin и Android в тесте не понадобится — это и есть выигрыш конструкторной инъекции.

4. **`PlayerEvent.ToggleShuffle`**: добавь событие, поле `shuffleEnabled` в `PlayerUiState` (оно уже есть в `PlaybackState`), вызывай `audioPlayer.setShuffleModeEnabled(...)`.

5. **`PlayerEvent.CycleRepeat`**: `RepeatMode.Off → All → One → Off`. Подсказка — `RepeatMode.entries` даёт список всех значений enum.

6. **Проверь `WhileSubscribed`**: поставь `SharingStarted.WhileSubscribed(0)`, добавь лог в `combine`-лямбду, поверни экран. Сравни количество пересозданий upstream с `5_000`.

---

## Дальше

→ [`07-NAVIGATION_AND_SCREENS.md`](./07-NAVIGATION_AND_SCREENS.md)

## Ссылки

- [StateFlow and SharedFlow — Kotlin docs](https://kotlinlang.org/docs/flow.html#stateflow-and-sharedflow)
- [Architecture: UI layer](https://developer.android.com/topic/architecture/ui-layer)
- [UI state production — sealed vs data class](https://developer.android.com/topic/architecture/ui-layer/state-production)
- [`stateIn` reference](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/state-in.html)
- [Side-effects in Compose](https://developer.android.com/jetpack/compose/side-effects)
- [Koin — injecting parameters](https://insert-koin.io/docs/reference/koin-core/injection-parameters)
