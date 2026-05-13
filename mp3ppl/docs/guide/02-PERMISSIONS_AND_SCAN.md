# 02. Разрешения и сканирование музыки

## Зачем

Чтобы получить список треков, есть два варианта:
1. **MediaStore** — системная БД Android, в которой медиасканер сам находит и индексирует все аудиофайлы. 
2. Быстро (запрос уже готовых метаданных),
2. не нужно обходить файловую систему вручную.
2. **Ручное сканирование** с `File.walkTopDown()` + чтение метаданных через JAudioTagger. Медленнее, но даёт полный контроль.

Для MVP выбираем **MediaStore**. Он решает задачу в 50 строк кода.

Но есть загвоздка: начиная с Android 13 (API 33) есть отдельное разрешение `READ_MEDIA_AUDIO`, которое надо запрашивать **в рантайме**.
Нельзя просто объявить в манифесте и получить доступ — пользователь должен явно согласиться.

---

## Что реализуем

По итогам этого этапа:

1. Перенесём `Track` из `shared/data` в `shared/domain` (модель — это домен).
2. Добавим `Album` в `domain`.
3. Сделаем `expect class MusicScanner` в `commonMain`, Android-реализацию и iOS-заглушку.
4. Починим баг в существующем `MusicScanner.android.kt`.
5. Добавим запрос разрешения в Compose через `rememberLauncherForActivityResult`.
6. Напишем `TracksRepository` с `Flow`, чтобы реактивно следить за списком.
7. Сделаем группировку `List<Track>` → `List<Album>`.

Файлы, которые появятся / изменятся:

```
shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/
├── Track.kt                      (перенос)
├── Album.kt                      (новый)
├── TracksRepository.kt           (новый)
└── AlbumsRepository.kt           (новый)

shared/data/src/
├── commonMain/kotlin/org/example/mp3player/data/
│   ├── MusicScanner.kt           (новый, expect)
│   ├── TracksRepositoryImpl.kt   (новый)
│   └── AlbumsRepositoryImpl.kt   (новый)
├── androidMain/kotlin/org/example/mp3player/data/
│   └── MusicScanner.android.kt   (переделать + фикс бага)
└── iosMain/kotlin/org/example/mp3player/data/
    └── MusicScanner.ios.kt       (новый, заглушка)

composeApp/src/androidMain/
├── AndroidManifest.xml           (добавить permission)
└── kotlin/.../MainActivity.kt   (использовать запрос)

composeApp/src/androidMain/kotlin/.../permissions/
└── AudioPermission.kt            (новый, Compose-обёртка)
```

---

## Реализация

### Шаг 1 — Перенести `Track` в `domain`

Сейчас `Track` лежит в `shared/data/src/commonMain/kotlin/org/example/mp3player/data/Track.kt`. Переноси в domain:

```kotlin
// shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/Track.kt
package org.example.mp3player.domain

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String,
    val path: String,
    val duration: Long,
    val coverUri: String? = null,
)
```

Обрати внимание: добавлено поле `albumId`. Оно нам понадобится, чтобы группировать треки в альбомы (два альбома с одним названием,
но разными исполнителями — это разные альбомы).

После переноса в `shared/data/build.gradle.kts` должна быть зависимость:

```kotlin
commonMain.dependencies {
    implementation(project(":shared:domain"))
    implementation(libs.kotlinx.coroutines.core)
}
```

### Шаг 2 — Добавить `Album` в `domain`

```kotlin
// shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/Album.kt
package org.example.mp3player.domain

data class Album(
    val id: String,            // = albumId из MediaStore
    val title: String,
    val artist: String,        // "Various Artists" если в альбоме разные
    val trackCount: Int,
    val coverUri: String?,
    val totalDurationMs: Long,
)
```

### Шаг 3 — Интерфейсы репозиториев

```kotlin
// shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/TracksRepository.kt
package org.example.mp3player.domain

import kotlinx.coroutines.flow.Flow

interface TracksRepository {
    /** Текущий список треков. Переэмитит после вызова [refresh]. */
    fun observeTracks(): Flow<List<Track>>

    /** Запускает пересканирование. Подписчики [observeTracks] получат новый список. */
    suspend fun refresh()
}
```

```kotlin
// shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/AlbumsRepository.kt
package org.example.mp3player.domain

import kotlinx.coroutines.flow.Flow

interface AlbumsRepository {
    /** Альбомы, сгруппированные по albumId. Обновляется при изменении списка треков. */
    fun observeAlbums(): Flow<List<Album>>

    /** Треки конкретного альбома, в порядке номера дорожки (пока просто по title). */
    fun observeTracksOfAlbum(albumId: String): Flow<List<Track>>
}
```

### Шаг 4 — `expect` MusicScanner

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/MusicScanner.kt
package org.example.mp3player.data

import org.example.mp3player.domain.Track

expect class MusicScanner {
    /**
     * Полное сканирование медиатеки.
     * Вызывать в фоновом диспатчере — может быть медленно на устройствах с тысячами треков.
     */
    suspend fun scanTracks(): List<Track>
}
```

### Шаг 5 — Android-реализация (фикс бага + `actual`)

Смотри внимательно — в существующем коде на строке 23 лишняя `}`:

```kotlin
val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0}"   // ← баг, лишняя }
```

Правильно:

```kotlin
val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
```

Такой SQL `IS_MUSIC != 0}` MediaStore отклонит (или вернёт пустой курсор, зависит от версии). Курсор будет не null,
а `while (moveToNext())` не сработает — и ты получишь пустой список, не понимая почему.

Полная актуализированная реализация:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt
package org.example.mp3player.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.mp3player.domain.Track

actual class MusicScanner(private val context: Context) {

    actual suspend fun scanTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
        )

        // Фильтры: только музыка, длительность > 10 сек (отсекаем рингтоны и случайные файлы)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf("10000")

        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)

                tracks += Track(
                    id = id.toString(),
                    title = c.getString(titleCol).orFallback("Без названия"),
                    artist = c.getString(artistCol).orFallback("Неизвестный исполнитель"),
                    album = c.getString(albumCol).orFallback("Неизвестный альбом"),
                    albumId = albumId.toString(),
                    path = c.getString(pathCol).orEmpty(),
                    duration = c.getLong(durationCol),
                    coverUri = albumArtUri(albumId),
                )
            }
        }

        tracks
    }

    private fun albumArtUri(albumId: Long): String =
        "content://media/external/audio/albumart/$albumId"

    private fun String?.orFallback(fallback: String): String =
        if (this.isNullOrBlank()) fallback else this
}
```

### Шаг 6 — iOS-заглушка

```kotlin
// shared/data/src/iosMain/kotlin/org/example/mp3player/data/MusicScanner.ios.kt
package org.example.mp3player.data

import org.example.mp3player.domain.Track

actual class MusicScanner {
    actual suspend fun scanTracks(): List<Track> {
        TODO("iOS implementation: использовать MPMediaQuery.songs()")
    }
}
```

### Шаг 7 — `TracksRepositoryImpl`

Реактивный слой поверх сканера: хранит текущий список в `MutableStateFlow`, перезаписывает его по `refresh()`.

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/TracksRepositoryImpl.kt
package org.example.mp3player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.mp3player.domain.Track
import org.example.mp3player.domain.TracksRepository

class TracksRepositoryImpl(
    private val scanner: MusicScanner,
) : TracksRepository {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val scanLock = Mutex()

    override fun observeTracks(): Flow<List<Track>> = _tracks.asStateFlow()

    override suspend fun refresh() {
        // Mutex, чтобы два параллельных refresh не стартовали одновременно.
        scanLock.withLock {
            _tracks.value = scanner.scanTracks()
        }
    }
}
```

#### Разбор по строкам

##### `private val _tracks = MutableStateFlow<List<Track>>(emptyList())`

`MutableStateFlow<T>` — это контейнер, который хранит **ровно одно текущее значение** типа `T` и одновременно является `Flow<T>`. Любой, кто подпишется на него, **сразу** получит текущее значение, а потом — каждое новое.

Сравни:
- Обычный `Flow<T>`: «холодная плёнка» — пока никто не вызвал `collect`, ничего не происходит. У него нет «текущего значения».
- `StateFlow<T>`: «горячее радио» — оно всегда что-то транслирует. Новый слушатель сразу слышит то, что играет прямо сейчас.

`emptyList()` — стартовое значение. Пока `refresh()` не позвали, экран увидит пустой список (а не зависнет в ожидании эмита).

Подчёркивание `_tracks` — это просто соглашение об именовании в Kotlin: «приватная мутабельная версия, наружу не показывать».

##### `private val scanLock = Mutex()`

`Mutex` — это очередь корутин. Внутри `withLock { ... }` может находиться **только одна** корутина одновременно. Остальные ждут.

Ключевое отличие от `synchronized(lock) { ... }`:

|                              | `synchronized` | `Mutex.withLock` |
|---                        |---                |---                       |
| Что блокирует   | **поток** — поток встаёт и ничего не делает | **корутину** — корутина «приостанавливается» (suspend), поток освобождается и берёт другую работу |
| Откуда берётся            | Java/JVM-примитив |  Корутинный примитив (`kotlinx.coroutines.sync`) |
| Реентрант |           Да (один поток может войти повторно) | **Нет** — повторный `withLock` из той же корутины = вечный дедлок |
| Можно из `suspend`        | Можно, но плохо: занимаем поток зря |   Идиоматично |

Если бы здесь стоял `synchronized`, то на время `scanTracks()` (а это запрос к MediaStore — сотни миллисекунд на большой библиотеке) мы держали бы один из 64 потоков `Dispatchers.IO` намертво. С `Mutex` поток свободен заниматься чем-то ещё, пока наша корутина «спит» в ожидании сканирования.

##### `override fun observeTracks(): Flow<List<Track>> = _tracks.asStateFlow()`

`asStateFlow()` — это **апкаст** до `StateFlow<List<Track>>`. Возвращается тот же самый объект `_tracks`, но через тип, у которого нет setter'а. Снаружи никто не сможет вызвать `_tracks.value = ...` — только подписаться и читать.

Зачем — это инкапсуляция: единственный способ положить туда новые треки — это пройти через `refresh()`. Если бы мы отдавали `_tracks` напрямую, любой компонент мог бы перезаписать значение мимо `Mutex` и сломать инвариант «одно сканирование одновременно».

##### `override suspend fun refresh()`

Модификатор `suspend` — это **обещание**: «эта функция может приостановиться». Вызвать её можно только из корутины (или из другой `suspend`-функции).

Что компилятор делает с `suspend`-функцией — он добавляет ей скрытый параметр `Continuation<T>`, который описывает «куда вернуться после паузы». Когда корутина внутри `suspend` доходит до точки приостановки (например, `withContext(IO)` или `delay(...)`), она **не блокирует поток**: поток продолжает выполнять другие корутины, а наша «засыпает», запомнив свой `Continuation`. Когда нужное событие произошло — рантайм возобновляет корутину, возможно уже на другом потоке.

Это и есть «корутина приостановилась»: не «поток встал в `Thread.sleep`», а «функция запомнила место и отдала поток обратно».

##### `scanLock.withLock { _tracks.value = scanner.scanTracks() }`

`withLock` — это `lock()` + `try { блок } finally { unlock() }`. Никакой магии: блокируется на входе, освобождается на выходе **даже при исключении**. Если корутину отменят прямо во время `scanTracks()` — лок всё равно отдадут.

Сценарий, ради которого `Mutex` и стоит: пользователь дважды нажал «Обновить» подряд.

1. Первый клик — корутина A: входит в `withLock`, блокирует mutex, начинает `scanTracks()` (это suspend, она «спит»).
2. Через 50 мс — второй клик — корутина B: входит в `refresh()`, доходит до `withLock` — mutex занят, корутина B приостанавливается прямо здесь.
3. Корутина A досканировала, присвоила `_tracks.value = ...`, вышла из `withLock`, отпустила mutex.
4. Корутина B автоматически просыпается, входит в `withLock`, начинает свой `scanTracks()`.

Без `Mutex` сценарий был бы: A и B сканируют одновременно, обе пишут в `_tracks.value`, но кто пишет последним — тот и победил. На MediaStore это не критично (запросы независимые), но уже на уровне БД или сети — гарантированный гонок.

##### `_tracks.value = scanner.scanTracks()`

Присваивание `.value` атомарно публикует новое значение всем подписчикам. С двумя оговорками:

- **Conflated.** Если подписчик ещё не успел обработать предыдущее значение, а пришло новое — он увидит только новое, промежуточное «потеряется». Для UI-стейта это нормально: мы хотим показать **последний** список, а не каждый промежуточный.
- **`distinctUntilChanged` встроено.** Если новое значение `equals` старому — подписчики не получат повторный эмит. Поэтому `data class Track(...)` важен (у него правильный `equals`): если список после нового сканирования совпал с предыдущим — UI не будет зря перерисовываться.

### Шаг 8 — `AlbumsRepositoryImpl` (группировка)

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt
package org.example.mp3player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.example.mp3player.domain.Album
import org.example.mp3player.domain.AlbumsRepository
import org.example.mp3player.domain.Track
import org.example.mp3player.domain.TracksRepository

class AlbumsRepositoryImpl(
    private val tracksRepository: TracksRepository,
) : AlbumsRepository {

    override fun observeAlbums(): Flow<List<Album>> =
        tracksRepository.observeTracks().map { tracks -> groupIntoAlbums(tracks) }

    override fun observeTracksOfAlbum(albumId: String): Flow<List<Track>> =
        tracksRepository.observeTracks().map { tracks ->
            tracks.filter { it.albumId == albumId }
                .sortedBy { it.title }
        }

    private fun groupIntoAlbums(tracks: List<Track>): List<Album> =
        tracks
            .groupBy { it.albumId }
            .map { (albumId, items) ->
                val artists = items.map { it.artist }.distinct()
                val artist = if (artists.size == 1) artists.first() else "Various Artists"

                Album(
                    id = albumId,
                    title = items.first().album,
                    artist = artist,
                    trackCount = items.size,
                    coverUri = items.firstOrNull { it.coverUri != null }?.coverUri,
                    totalDurationMs = items.sumOf { it.duration },
                )
            }
            .sortedBy { it.title.lowercase() }
}
```

#### Разбор по строкам

Этот класс — главный пример «делать почти ничего, но получать реактивность бесплатно». Альбомы тут нигде не хранятся в поле; они **выводятся** из текущего списка треков на лету.

##### `tracksRepository.observeTracks().map { tracks -> groupIntoAlbums(tracks) }`

Здесь два важных момента, которые сливаются в одну строку.

**`observeTracks()`** возвращает `Flow<List<Track>>`. Это «провод», по которому каждый раз, когда `TracksRepositoryImpl` обновляет свой `_tracks.value`, прилетает свежий список.

**`.map { ... }`** — это **`Flow.map`** из `kotlinx.coroutines.flow`, оператор потока. Он вешается на провод и говорит: «когда по проводу прилетит `tracks`, прогони его через эту функцию и эмитни результат дальше». Сама `groupIntoAlbums` ничего не подписывает — она вызовется **на каждый emit upstream**.

Важно не путать этот `Flow.map` с другим `.map` ниже — `items.map { it.artist }` (см. дальше). Имена одинаковые, но это **разные функции с разной семантикой**:

|            | `Flow.map` (тут) | `List.map` (дальше) |
|---              |---            |-              --|
| Receiver         | `Flow<T>`         |            `List<T>` |
| Когда выполняется | На каждый emit upstream-flow | Один раз, синхронно |
| Возвращает | Новый `Flow<R>` | Новый `List<R>` |
| Импорт | `kotlinx.coroutines.flow.map` | `kotlin.collections` (даже импорт не нужен) |

То, что у них одинаковое имя — это совпадение API: и потоки, и коллекции естественно поддерживают «трансформацию каждого элемента». Под капотом это абсолютно разный код.

##### `tracks.filter { it.albumId == albumId }.sortedBy { it.title }`

В `observeTracksOfAlbum` — те же `Flow.map` снаружи, а внутри — стандартные `List.filter` и `List.sortedBy` (синхронные). Сначала отфильтровали треки этого альбома, потом отсортировали по названию.

##### `groupIntoAlbums(tracks: List<Track>): List<Album>` — пайплайн

Дальше идёт цепочка из четырёх вызовов на `List<Track>`. Разберём каждый.

##### `tracks.groupBy { it.albumId }`

Возвращает `Map<String, List<Track>>`. Ключ — `albumId` каждого трека, значение — список всех треков с этим `albumId`.

Конкретный пример. Допустим, на устройстве 5 треков:

```
Track("1", title="Money",        albumId="42")
Track("2", title="Time",         albumId="42")
Track("3", title="Brain Damage", albumId="42")
Track("4", title="Wish You Were Here", albumId="7")
Track("5", title="Have a Cigar",       albumId="7")
```

После `groupBy { it.albumId }`:

```
"42" -> [Track("1", "Money", ...), Track("2", "Time", ...), Track("3", "Brain Damage", ...)]
"7"  -> [Track("4", "Wish You Were Here", ...), Track("5", "Have a Cigar", ...)]
```

Гарантия: пустых групп `groupBy` **не создаёт**. В каждой паре «ключ → список» список содержит как минимум один элемент. Это пригодится дальше — мы будем спокойно звать `items.first()`.

##### `.map { (albumId, items) -> Album(...) }`

Это **`Map.map { entry -> ... }`** из stdlib (НЕ `Flow.map`!). На входе — `Map.Entry<String, List<Track>>`, на выходе — `List<Album>`.

`(albumId, items)` — это **деструктуризация**. У `Map.Entry` есть `componentN`-функции, которые позволяют записать `entry.key` и `entry.value` короче, через паттерн в скобках. Эквивалентная запись без деструктуризации:

```kotlin
.map { entry ->
    val albumId = entry.key
    val items = entry.value
    Album(...)
}
```

Дальше внутри лямбды мы строим `Album` из группы треков.

##### `val artists = items.map { it.artist }.distinct()`

Здесь `.map { it.artist }` — это **`List.map`** (синхронный). Из списка треков сделали список имён исполнителей.

`.distinct()` — оставляет только уникальные значения, сохраняя порядок. Алгоритм: проходит по списку, держит `LinkedHashSet`, добавляет туда; на выходе — содержимое set'а как список.

Зачем: альбом может содержать треки разных исполнителей (сборники, фит-треки). Нам важно — все ли исполнители одинаковые или нет.

##### `val artist = if (artists.size == 1) artists.first() else "Various Artists"`

Если в альбоме все треки от одного и того же исполнителя — пишем его имя. Если от двух и более разных — стандартная пометка «Various Artists» (так делают все плееры).

##### `title = items.first().album`

Берём название альбома из первого трека группы. Это безопасно потому, что `groupBy` гарантирует непустые списки. Если бы мы не были уверены — пришлось бы `items.firstOrNull()?.album ?: "Без названия"`.

Почему всех треков альбома спрашивать не надо: в норме у всех треков одного `albumId` поле `album` (название) совпадает. MediaStore сам это обеспечивает — `albumId` и есть хеш названия + исполнителя.

##### `coverUri = items.firstOrNull { it.coverUri != null }?.coverUri`

Здесь хитрая деталь. Берём **первый** трек, у которого `coverUri != null` — а не просто `items.first().coverUri`.

Сценарий, ради которого это важно: альбом из 10 треков, у первых трёх MediaStore не нашёл обложку (`null`), а у четвёртого нашёл. Если бы мы взяли `items.first().coverUri` — получили бы `null` и показали бы плейсхолдер. С `firstOrNull { it.coverUri != null }` — найдём ту обложку, что есть.

Разбираем выражение по частям:
- `firstOrNull { предикат }` — возвращает первый элемент, удовлетворяющий предикату, или `null` если такого нет.
- `?.coverUri` — safe call: если результат не null, прочитай его `coverUri`; если null — оставь `null`.

##### `totalDurationMs = items.sumOf { it.duration }`

`sumOf` — стандартный функция-агрегат. Прогоняет лямбду по каждому элементу, складывает результаты. Эквивалентно `items.map { it.duration }.sum()`, но без промежуточного списка.

Получаем суммарную длительность альбома в миллисекундах — пригодится показать «42 трека • 3 ч 12 мин».

##### `.sortedBy { it.title.lowercase() }`

Сортировка списка `Album`-ов по названию альбома, **с приведением к нижнему регистру**.

Без `.lowercase()` сравнение шло бы по кодпойнтам Unicode напрямую. А там:

| Символ | Кодпойнт |
|---|---|
| `A`–`Z` | 65–90 |
| `a`–`z` | 97–122 |
| `А`–`Я` | 1040–1071 |
| `а`–`я` | 1072–1103 |

То есть **`Z` (90) < `a` (97) < `Я` (1071) < `я` (1103)**. Без `.lowercase()`:
- Альбом `"banana"` шёл бы **после** `"Apple"` (потому что `b` > `A`), но это случайно: `b` (98) > `A` (65). Если бы был `"apple"` и `"Banana"`, вышло бы наоборот.
- Кириллица улетела бы в самый конец списка, **после** всей латиницы.
- А `"яблоко"` оказалось бы после `"Я"` (потому что `я` > `Я`).

С `.lowercase()` сортировка получается чувствительной только к буквам, не к регистру. `"Apple"` и `"apple"` встанут рядом, кириллица перемешается с латиницей по алфавиту.

> **Замечание для перфекциониста.** `lowercase()` без локали работает не идеально для тонких случаев — турецкая `İ` → `i̇` (с точкой), немецкая `ß` → `ss`. Идеально правильная сортировка — через `Collator` (JVM) или ICU, но это несоразмерно сложнее. Для MVP `.lowercase()` — компромисс «в 100 раз лучше, чем без него».

### Шаг 9 — Permission в манифесте

`composeApp/src/androidMain/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Android 13 (API 33) и выше -->
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

    <!-- Android 12 (API 32) и ниже -->
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <application ...>
        ...
    </application>
</manifest>
```

`maxSdkVersion="32"` означает: на Android 13+ это разрешение даже не запрашивается, там работает `READ_MEDIA_AUDIO`.

### Шаг 10 — Compose-обёртка для запроса разрешения

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/permissions/AudioPermission.kt
package org.example.mp3player.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

enum class AudioPermissionState { Granted, Denied, Unknown }

@Composable
fun rememberAudioPermissionState(): Pair<AudioPermissionState, () -> Unit> {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var state by remember {
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED
            ) AudioPermissionState.Granted
            else AudioPermissionState.Unknown
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        state = if (granted) AudioPermissionState.Granted else AudioPermissionState.Denied
    }

    val request = remember(launcher) { { launcher.launch(permission) } }

    return state to request
}
```

Использование в экране:

```kotlin
@Composable
fun TracksScreen(viewModel: TracksViewModel = koinViewModel()) {
    val (permissionState, requestPermission) = rememberAudioPermissionState()

    LaunchedEffect(permissionState) {
        if (permissionState == AudioPermissionState.Granted) {
            viewModel.onEvent(TracksEvent.Load)
        }
    }

    when (permissionState) {
        AudioPermissionState.Granted -> TracksContent(viewModel)
        AudioPermissionState.Denied -> PermissionDeniedBanner(onRetry = requestPermission)
        AudioPermissionState.Unknown -> PermissionRequestBanner(onRequest = requestPermission)
    }
}
```

Подробно `TracksScreen` и `TracksViewModel` разберём в файлах `06` и `07`. Здесь главное — понять, где и когда запрашивается разрешение.

#### Разбор по строкам — `rememberAudioPermissionState`

##### `enum class AudioPermissionState { Granted, Denied, Unknown }`

Здесь — `enum`, а не `sealed interface`, потому что у нас три **константных** состояния, каждое без собственных данных. Если бы понадобилось «`Denied(reason: String)`» — пришлось бы переключаться на `sealed interface`, потому что у `enum` нет полей-переменных.

##### `val context = LocalContext.current`

`LocalContext` — это `CompositionLocal`, специальный механизм Compose, который позволяет передать значение «вниз по дереву композиций» без явной передачи через параметры. Корневой `setContent { ... }` положил туда текущий `Context`, а любой Composable ниже может его взять через `.current`.

Думай об этом как о неявной переменной в области видимости: «во всём поддереве — этот Context».

##### `var state by remember { mutableStateOf(...) }`

В этой строке три отдельных механизма, которые часто путают.

**`mutableStateOf(value)`** создаёт `MutableState<T>` — наблюдаемый «контейнер» с одним полем `.value`. Когда `.value` меняется, Compose замечает это и **перерисовывает** все Composable-функции, которые читали `.value`. Это и есть «состояние, которое видит Compose».

**`remember { ... }`** — это «удержать значение между рекомпозициями». Сама `rememberAudioPermissionState()` будет вызвана много раз (при каждой рекомпозиции экрана), но `remember` запоминает результат лямбды **в первый раз** и при последующих вызовах возвращает тот же объект. Без `remember` мы создавали бы новый `MutableState` каждый раз, теряя предыдущее значение.

**`var ... by ...`** — это **property delegation**. Когда пишешь `var state by mutableState`, компилятор подставляет:
- `state` (чтение) → `mutableState.getValue(this, ::state)` → `mutableState.value`
- `state = ...` (запись) → `mutableState.setValue(this, ::state, ...)` → `mutableState.value = ...`

То есть `state` ведёт себя как обычная переменная, но под капотом каждое чтение/запись ходит в `MutableState.value`. Это синтаксический сахар, чтобы не писать `state.value` каждый раз.

Сравни три эквивалентные записи:

```kotlin
// Полная — без by:
val stateContainer: MutableState<AudioPermissionState> = remember { mutableStateOf(...) }
// чтение: stateContainer.value
// запись: stateContainer.value = AudioPermissionState.Granted

// С by — компактнее:
var state by remember { mutableStateOf(...) }
// чтение: state
// запись: state = AudioPermissionState.Granted
```

##### `rememberLauncherForActivityResult(...)`

Activity Result API — современная замена `onActivityResult`. Идея: «я хочу запустить системный экран и получить результат, при этом не зависеть от lifecycle Activity вручную».

`rememberLauncherForActivityResult` — обёртка для Compose:
- регистрирует launcher в `ActivityResultRegistry` Activity (нужно делать **до** `onStart`, поэтому отдельный composable hook),
- помнит его между рекомпозициями (тот же `remember` под капотом),
- возвращает `ManagedActivityResultLauncher` с методом `.launch(input)`.

Когда вызывается `launcher.launch(permission)`:
1. Android показывает системный диалог разрешений.
2. Пользователь нажимает «Разрешить» / «Отклонить».
3. Activity получает результат, передаёт в registry.
4. Registry находит наш зарегистрированный callback и вызывает лямбду `(granted) -> { ... }`.

Лямбда вызывается **на главном потоке**, поэтому в ней безопасно менять Compose-state (`state = if (granted) ...`).

`contract = ActivityResultContracts.RequestPermission()` — это «шаблон взаимодействия»: вход — `String` (имя permission), выход — `Boolean` (granted). Под капотом контракт умеет упаковать вход в Intent и распаковать результат. Контракты есть и на другие сценарии: `PickVisualMedia`, `TakePicture`, `OpenDocument`.

##### `val request = remember(launcher) { { launcher.launch(permission) } }`

Здесь **двойные фигурные** в одну строку — две разных конструкции одна в одной.

Внешние скобки — лямбда, которую `remember` запоминает (это её провайдер начального значения).
Внутренние скобки — лямбда, которую мы хотим **сохранить как значение**: `() -> Unit`, которая при вызове запустит permission request.

Развернуто это выглядит так:

```kotlin
val request: () -> Unit = remember(launcher) {
    val capturedLauncher = launcher
    val capturedPermission = permission
    return@remember { capturedLauncher.launch(capturedPermission) }
}
```

`remember(launcher)` с **ключом** означает: «запомни значение, пока ключ `launcher` не поменялся». Если `launcher` пересоздастся (теоретически — после Activity recreate) — лямбда `request` тоже пересоздастся с новым launcher внутри.

Зачем это вообще: если бы мы передавали `{ launcher.launch(permission) }` напрямую в дочерний Composable, Compose видел бы «новую лямбду на каждой рекомпозиции» и считал бы дочерние Composable нестабильными → лишние рекомпозиции. С `remember` лямбда стабильная — ссылка одна и та же.

##### `return state to request`

`a to b` — это инфиксная функция-фабрика для `Pair<A, B>`. Эквивалентно `Pair(state, request)`.

В вызывающем коде это раскладывается обратно через деструктуризацию: `val (permissionState, requestPermission) = rememberAudioPermissionState()`. Та же механика, что у `(albumId, items)` в `groupBy` — `Pair` тоже имеет `component1`/`component2`.

---

## Разбор

### `withContext(Dispatchers.IO)` — что физически происходит

```kotlin
actual suspend fun scanTracks(): List<Track> = withContext(Dispatchers.IO) { ... }
```

`Dispatchers` — это объекты, которые умеют **запускать корутину на нужном пуле потоков**. У kotlinx.coroutines их три ходовых:

| Диспетчер | Где живёт | Для чего |
|---|---|---|
| `Dispatchers.Main` | Один Android UI-thread | Всё, что трогает Compose/View — рисование, чтение состояния, обновление UI |
| `Dispatchers.Default` | Пул на `Runtime.availableProcessors()` потоков | CPU-интенсивная работа (парсинг, обработка изображений, сортировка миллиона элементов) |
| `Dispatchers.IO` | Пул до 64 потоков (можно конфигурировать), потоки могут «висеть в ожидании» | Блокирующие I/O — файлы, сеть, БД, `ContentResolver` |

Различие `Default` vs `IO` — про допустимое блокирование. Поток в `Default`-пуле должен крутить вычисление и быстро отдать управление; `IO`-пул специально **рассчитан** на то, что поток может стоять и ждать ответа от диска или сокета.

**Что делает `withContext(Dispatchers.IO) { блок }`:**

1. `withContext` — это **suspend-функция**. Когда её вызывают, корутина приостанавливается (suspend point).
2. Рантайм планирует наш блок на `Dispatchers.IO` (то есть «найди свободный поток в IO-пуле и выполни блок там»).
3. Текущий поток (например, Main) **освобождается** — он не ждёт; он берёт следующую задачу.
4. Когда блок отработал — рантайм возобновляет нашу корутину **обратно в исходном контексте** (откуда мы пришли — в Main, если из ViewModel).
5. `withContext` возвращает значение, которое вернул блок (`return@withContext` или последнее выражение).

То есть `withContext` — это **не «запусти параллельно»**, а **«временно переключись, дождись результата, вернись»**.

#### `withContext` vs `launch`

Очень частая путаница, поэтому отдельно.

```kotlin
withContext(Dispatchers.IO) { scanTracks() }   // дожидается результата, возвращает List<Track>
launch(Dispatchers.IO) { scanTracks() }        // запускает параллельно, возвращает Job, не ждёт
```

- `withContext` — suspend-функция. Возвращает значение блока. Текущая корутина «спит» до завершения.
- `launch` — обычная функция (нужен `CoroutineScope`). Запускает **новую** корутину, возвращает `Job`. Текущая корутина продолжает выполнение **сразу же**, не дожидаясь.

В нашем сканере нам нужно дождаться списка треков, поэтому — `withContext`.

#### Что было бы без переключения

Если убрать `withContext(Dispatchers.IO)`:

```kotlin
actual suspend fun scanTracks(): List<Track> {
    val tracks = mutableListOf<Track>()
    context.contentResolver.query(...) // ← блокирующий вызов, диск + IPC до MediaStore
    // ...
    return tracks
}
```

`scanTracks()` сама ничего не переключает; она выполнится **на том диспетчере, с которого её позвали**. Если позвали из `viewModelScope.launch { scanTracks() }` — это `Dispatchers.Main`. На устройстве с 5000 треков `query` + чтение курсора может занять секунду-две. Всё это время:

- Compose не может перерисовывать UI (Main занят).
- Тапы по экрану копятся в очередь.
- Через 5 секунд Android покажет ANR-диалог («Application Not Responding»).

С `withContext(Dispatchers.IO)` Main свободен, ANR не случится, UI отзывчивый.

### `cursor?.use { ... }` — try-with-resources в Kotlin

`Cursor` — это **ресурс**: внутри него открытое соединение с системной БД медиа, нативная память, файловые дескрипторы. Если не вызвать `close()`, ресурс утечёт. Кроме памяти, это могут быть лимиты ОС — на Android количество одновременно открытых cursor'ов конечно.

`?.use` — комбинация двух механизмов:

- **`?.`** — safe call. `query(...)` возвращает `Cursor?` (может быть `null`, если что-то пошло не так с провайдером). `?.use` означает «если не null — вызови `use`».
- **`.use { блок }`** — extension-функция на `Closeable`. Эквивалентно:

```kotlin
public inline fun <T : Closeable?, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        this?.close()
    }
}
```

То есть `cursor.use { c -> ... }` — это `try { ...работа с c... } finally { c.close() }`. Закрытие гарантировано даже если внутри блока вылетело исключение.

Анти-паттерн (так делать нельзя):

```kotlin
val c = context.contentResolver.query(...)
while (c?.moveToNext() == true) { ... }
c?.close()   // если выше выскочит NPE/исключение — close() не вызовется
```

С `use` такая ошибка невозможна по конструкции.

### `String?.orFallback(fallback)` — extension на nullable receiver

```kotlin
private fun String?.orFallback(fallback: String): String =
    if (this.isNullOrBlank()) fallback else this
```

Здесь сразу несколько Kotlin-фишек.

**Extension-функция.** Синтаксис `fun String?.orFallback(...)` означает «добавляю функцию `orFallback` ко всем выражениям типа `String?`». Под капотом — это статический метод, в который `this` передаётся первым параметром, но снаружи ты вызываешь её как метод: `someString.orFallback("...")`.

**Receiver — `String?` (nullable).** Это важно: receiver сам может быть `null`. Внутри тела `this` — это `String?`, а не `String`. Поэтому ты можешь вызвать функцию даже на null-значении: `null.orFallback("X")` вернёт `"X"`, никакого NPE не будет.

**`isNullOrBlank()`** — стандартная extension-функция в Kotlin stdlib, тоже работающая на `String?`. Возвращает `true` если строка `null`, пустая или состоит только из пробелов. Это покрывает все три случая «бесполезное значение из MediaStore».

**`else this`** — после `if (this.isNullOrBlank())` smart cast не сработает (`isNullOrBlank` — это пользовательский предикат с точки зрения компилятора, не `if (this == null)`). Но возвращать `this` нам компилятор разрешает: тип возвращаемой функции `String`, `this` имеет тип `String?` — а компилятор уже понимает, что после `if (this.isNullOrBlank()) return fallback`, оставшийся путь идёт по ветке «не null и не blank», и `this` приводится к `String` автоматически.

### `Mutex.withLock`

Подробный разбор — см. «Разбор по строкам» в Шаге 7 (`TracksRepositoryImpl`). Кратко: блокирует **корутину**, а не поток; не реентрант (повторный вход из той же корутины = дедлок); идиоматичный «корутинный замок».

### `Flow.map` vs `List.map`

Подробный разбор — см. «Разбор по строкам» в Шаге 8 (`AlbumsRepositoryImpl`). Кратко: имена одинаковые, суть разная. `Flow.map` — оператор холодного потока, лямбда выполняется на каждый emit upstream. `List.map` — синхронная трансформация коллекции, выполняется один раз.

### `groupBy { it.albumId }`

Подробный разбор — см. «Разбор по строкам» в Шаге 8. Кратко: возвращает `Map<String, List<Track>>`, гарантирует непустые группы.

### `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)`

Android не даёт одну универсальную константу для "читать аудио". На API 33+ это `READ_MEDIA_AUDIO`, раньше — `READ_EXTERNAL_STORAGE`. Проверка по `Build.VERSION.SDK_INT` — стандартный способ рантайм-совместимости.

`Build.VERSION_CODES.TIRAMISU` — это просто `33`, константа из SDK. Имена-кодовые («Tiramisu», «UpsideDownCake») выпускаются вместе с релизом Android: 33 = Tiramisu = Android 13.

Что было бы без проверки: попытка запросить `Manifest.permission.READ_MEDIA_AUDIO` на Android 12 (API 32) — система **не знает** такого permission, в манифесте его не объявить как обязательный (или объявить, но он будет проигнорирован). А `READ_EXTERNAL_STORAGE` на Android 13+ перестал давать доступ к аудио — Google разделил пермишены по типам медиа. Поэтому строго: 33+ = `READ_MEDIA_AUDIO`, ниже = `READ_EXTERNAL_STORAGE`.

Обрати внимание: в манифесте мы объявили **оба** permission'а с разными `maxSdkVersion`, поэтому на каждой версии Android запрашивается ровно один. Манифест и runtime-проверка должны совпадать — иначе на одной из версий разрешение не сработает.

---

## Подводные камни

### 1. Баг с лишней `}` в selection
`IS_MUSIC != 0}` → курсор валидный, но пустой. Ты думаешь, что треков нет. 
Проверь логом количество: `Log.d("Scanner", "Found ${tracks.size} tracks")`.

### 2. Забытый `Dispatchers.IO`
Сканирование в `Main` → UI замерзает. На устройстве с 5000 треков — на пару секунд. ANR (Application Not Responding) при >5 секунд.

### 3. Не закрыли курсор
Если не использовать `use {}` — утечка. С одним курсором незаметно, с тысячей — OutOfMemory.

### 4. Разрешение дано, но `MediaStore` пустой
Возможно, MediaStore ещё не проиндексировал файлы (например, только что скопировал MP3 через ADB).
Решение: `MediaScannerConnection.scanFile(...)` или перезагрузка устройства. На боевых устройствах это редко проблема.

### 5. `MediaStore.Audio.Media.Duration` вместо `DURATION`
В текущем коде проекта (строка 95 из старого файла `SCAN_MUSIC.md`) было `Duration` с заглавной M.
Это **не константа MediaStore** — там `DURATION`. Если скопируешь неправильно — ошибка компиляции.

### 6. Экран не запрашивает permission на первом запуске
Compose-обёртка выше проверяет permission в `remember { }` — это срабатывает при первой композиции. 
Если пользователь **уже** отказал один раз, статус станет `Unknown` → `Denied` после попытки. Надо показать баннер с кнопкой "Запросить ещё раз".

### 7. iOS-заглушка не компилируется
Если `actual class MusicScanner` в `iosMain` не имеет того же конструктора и 
методов с `actual`-модификатором, что и `expect` — ошибка компиляции **только при сборке под iOS**. Android соберётся. Всегда собирай оба таргета.

---

## Try yourself

1. **Проверь фикс бага**: после переписывания `MusicScanner.android.kt` 
2. запусти приложение, добавь в `TracksScreen` временный `Text(text = "Found: ${tracks.size}")`. Должно показать количество >0.

2. **Поиграйся с фильтром**: измени `DURATION > 10000` на `> 60000` — пропадут все треки короче минуты. Возвращай как было.

3. **Добавь поле `year`**: в `Track` добавь `val year: Int? = null`, в projection — `MediaStore.Audio.Media.YEAR`, прочитай и заполни.

4. **Группировка по исполнителю**: добавь метод `observeTracksByArtist(artist: String): Flow<List<Track>>` в `TracksRepository`.
5. Реализация как `observeTracksOfAlbum`, но через `filter { it.artist == artist }`.

5. **Тест**: на эмуляторе нет треков по умолчанию. Скачай пару MP3, положи через Device Manager в `/sdcard/Music/`, 
6. перезапусти приложение. Если не видит — `MediaScannerConnection.scanFile(context, arrayOf(path), null, null)`.

---

## Дальше

→ [`03-DATABASE_ROOM.md`](./03-DATABASE_ROOM.md)

## Ссылки

- [MediaStore — Android Developers](https://developer.android.com/reference/android/provider/MediaStore)
- [Runtime permissions](https://developer.android.com/training/permissions/requesting)
- [Activity Result APIs in Compose](https://developer.android.com/jetpack/compose/side-effects#rememberupdatedstate)
- [Kotlin Flow — map and transform](https://kotlinlang.org/docs/flow.html#intermediate-flow-operators)
