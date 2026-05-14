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

`Track` сейчас лежит в `shared/data/...` — но это модель данных, её место в `domain`. Переносим файл и заодно добавляем поле `albumId`, без которого следующие шаги не сработают.

Создаём файл `shared/domain/.../Track.kt`:

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

Поле `albumId` — главное добавление. Без него не сгруппировать треки в альбомы правильно: два альбома с одним названием, но разными исполнителями — это разные альбомы, и отличает их именно `albumId` (его MediaStore присваивает на уровне «название + исполнитель»).

После переноса прописываем зависимость в Gradle, чтобы `shared:data` видел модели из `shared:domain`:

```kotlin
// shared/data/build.gradle.kts

commonMain.dependencies {
    implementation(project(":shared:domain"))
    implementation(libs.kotlinx.coroutines.core)
}
```

Теперь все импорты `org.example.mp3player.domain.Track` будут компилироваться в `data`-модуле.

### Шаг 2 — Добавить `Album` в `domain`

Альбом — отдельная модель, со своими полями, которых нет у трека: количество дорожек, суммарная длительность, агрегированный исполнитель.

Создаём файл `shared/domain/.../Album.kt`:

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

Главное про эту модель — она **не хранится** в БД и не собирается вручную. На Шаге 8 мы выведем `List<Album>` из `List<Track>` через `groupBy`. Сейчас просто фиксируем форму данных.

Заметки по полям:
- `id` — это `albumId` из MediaStore (строкой, чтобы единообразно с `Track.albumId`).
- `artist` — `"Various Artists"` если в альбоме треки разных исполнителей; логику решения соберём в `AlbumsRepositoryImpl`.
- `coverUri: String?` — может быть `null`, если у всех треков альбома обложку MediaStore не нашёл.
- `totalDurationMs` — миллисекунды; форматирование в «3 ч 12 мин» — задача UI-слоя.

### Шаг 3 — Интерфейсы репозиториев

Репозиторий — это абстракция «откуда берём данные». Интерфейс лежит в `domain` (чистый Kotlin, без зависимостей от Android), а реализация — в `data`. Это даёт три преимущества: тестировать ViewModel можно с фейковым репозиторием, реализацию можно поменять (добавить кэш, новый источник) без правок UI, и `domain` остаётся переиспользуемым на iOS.

Создаём `TracksRepository.kt`:

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

`Flow<List<Track>>` — это «горячая трубка»: каждый раз, когда список треков меняется, прилетает новый снимок. Подробнее `Flow` разберём в Шаге 7, где появится первая реализация.

`suspend fun refresh()` — корутинная функция (не обычная). Сканирование MediaStore блокирующее, и `suspend` — это контракт «зови меня из корутины, я могу заснуть».

Дальше — `AlbumsRepository.kt`:

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

Тут даже нет `suspend` — альбомы выводятся из треков на лету, своего «обновления» им не нужно. Когда подписчик `observeAlbums()` подцепляется, он автоматически переэмитит при каждом обновлении треков (это соберём в Шаге 8).

`observeTracksOfAlbum(albumId)` — отдельный метод, потому что «треки альбома X» — частая операция и логично иметь её прямо тут, а не делать `observeTracks().map { it.filter { … } }` в каждом ViewModel.

### Шаг 4 — `expect` MusicScanner

KMP-механика: один тип, разные реализации на каждой платформе. В `commonMain` пишем `expect`-заголовок (контракт), в `androidMain` и `iosMain` — `actual`-реализации.

Создаём файл `MusicScanner.kt` в `commonMain`:

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

`expect class MusicScanner` — обещание компилятору: «тип с таким именем и такими методами будет, конкретная реализация — в платформенных source set'ах». Если для какой-то платформы `actual class MusicScanner` не написан — модуль для этой платформы не соберётся (это и есть гарантия покрытия).

`suspend fun scanTracks()` — обязательно `suspend` уже на уровне `expect`, потому что любая реализация будет блокирующей (диск, IPC, нативный медиа-API). `actual` обязан сохранить ту же подпись — добавить или убрать `suspend` нельзя.

### Шаг 5 — Android-реализация (фикс бага + `actual`)

Сначала — баг в существующем коде. В файле `MusicScanner.android.kt` (строка ~23) лишняя закрывающая `}` в SQL-выражении:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt

val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0}"   // ← баг, лишняя }
```

Правильно:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt

val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
```

Такой SQL `IS_MUSIC != 0}` MediaStore отклонит (или вернёт пустой курсор — зависит от версии). Курсор будет не `null`, а `while (moveToNext())` не сработает — и ты получишь пустой список, не понимая почему.

Дальше — собираем полную реализацию пошагово.

Создаём файл — пакет, импорты, класс с конструктором (тело пока пустое):

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

    // дальше — actual suspend fun scanTracks() для сканирования медиатеки и два private helper'а (albumArtUri, orFallback)
}
```

`Context` нужен, чтобы достучаться до `contentResolver` — единственный способ задать запрос к `MediaStore`. На iOS его не будет: там `actual` будет без параметров (см. Шаг 6).

Внутри класса — главный метод `scanTracks`. Снаружи он `suspend`, внутри сразу переключается на IO-диспатчер:

```kotlin
actual class MusicScanner(private val context: Context) {

    actual suspend fun scanTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()

        // дальше внутри withContext — projection (колонки запроса), selection (фильтр), query + обход курсора (сборка List<Track>), return tracks
    }
}
```

`Dispatchers` — это объекты, которые умеют запускать корутину на нужном пуле потоков. У kotlinx.coroutines их три ходовых:

| Диспетчер | Где живёт | Для чего |
|---|---|---|
| `Dispatchers.Main` | Один Android UI-thread | Всё, что трогает Compose/View — рисование, чтение состояния, обновление UI |
| `Dispatchers.Default` | Пул на `Runtime.availableProcessors()` потоков | CPU-интенсивная работа (парсинг, сортировка миллиона элементов) |
| `Dispatchers.IO` | Пул до 64 потоков, потоки могут «висеть в ожидании» | Блокирующие I/O — файлы, сеть, БД, `ContentResolver` |

`Default` vs `IO` — про допустимое блокирование. `Default`-пул ждёт быстро отдающих CPU задач; `IO`-пул рассчитан на то, что поток может стоять и ждать ответа от диска.

Что делает `withContext(Dispatchers.IO) { блок }`:

1. `withContext` — это suspend-функция. При вызове корутина приостанавливается.
2. Рантайм планирует наш блок на свободный поток в IO-пуле.
3. Текущий поток (например, Main) **освобождается** — он не ждёт, берёт следующую задачу.
4. Когда блок отработал — рантайм возобновляет корутину в исходном контексте (откуда пришли).
5. `withContext` возвращает значение, которое вернул блок (последнее выражение).

То есть `withContext` — **не «запусти параллельно»**, а **«временно переключись, дождись результата, вернись»**.

Частая путаница — `withContext` vs `launch`:

```kotlin
withContext(Dispatchers.IO) { scanTracks() }   // дожидается результата, возвращает List<Track>
launch(Dispatchers.IO) { scanTracks() }        // запускает параллельно, возвращает Job, не ждёт
```

`launch` — обычная функция (нужен `CoroutineScope`), запускает новую корутину, возвращает `Job`. Текущая корутина продолжается сразу, не дожидаясь. Нам нужен результат — поэтому `withContext`.

Без переключения было бы плохо: `scanTracks()` сама ничего не переключает, она выполнится на том диспатчере, с которого её позвали. Из `viewModelScope.launch { scanTracks() }` это `Main`. На устройстве с 5000 треков `query` + чтение курсора занимает секунду-две — за это время Compose не может перерисовать UI (Main занят), тапы копятся в очередь, через 5 секунд Android покажет ANR-диалог. С `withContext(Dispatchers.IO)` Main свободен.

Внутри `withContext` — сначала проекция (какие колонки нужны):

```kotlin
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

        // дальше — selection (фильтр: только музыка длиннее 10 сек), sortOrder (порядок) и сам query
    }
```

`projection` — это «SELECT columns» для MediaStore. Передавать `null` тоже можно (вернёт все колонки), но запрашивать явно — быстрее и чётче по интенту.

Дальше — фильтр (только музыка, длиннее 10 секунд) и сортировка:

```kotlin
    actual suspend fun scanTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()

        val projection = arrayOf( /* ... */ )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf("10000")

        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        // дальше — query к MediaStore и обход курсора для сборки List<Track>
    }
```

`IS_MUSIC != 0` отсекает не-музыкальные аудио (рингтоны, нотификации). `DURATION > ?` (10000 мс) отсекает случайные короткие файлы. `?` — параметризованный запрос: значение подставляется через `selectionArgs`, а не через интерполяцию строк, что защищает от SQL-инъекций (даже если MediaStore к ним устойчив, привычку лучше беречь).

`COLLATE NOCASE` — сортировка без учёта регистра прямо на уровне БД. Идеально работает для латиницы, для кириллицы — не идеально (решим в Шаге 8 через `lowercase()`).

Сам запрос с обходом курсора и сборкой `Track`-ов:

```kotlin
    actual suspend fun scanTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()

        val projection = arrayOf( /* ... */ )
        val selection = /* ... */
        val selectionArgs = arrayOf("10000")
        val sortOrder = /* ... */

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
```

Тут две идиомы, без которых легко словить утечку — `?.` и `.use`.

`Cursor` — это **ресурс**: внутри открытое соединение с системной БД медиа, нативная память, файловые дескрипторы. Если не вызвать `close()`, ресурс утечёт. Количество одновременно открытых cursor'ов на Android конечно.

`?.use` — комбинация двух механизмов:
- **`?.`** — safe call. `query(...)` возвращает `Cursor?` (может быть `null`, если что-то пошло не так с провайдером). `?.use` означает «если не `null` — вызови `use`».
- **`.use { блок }`** — extension на `Closeable`. Эквивалентно:

```kotlin
public inline fun <T : Closeable?, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        this?.close()
    }
}
```

То есть `cursor.use { c -> ... }` — это `try { ...работа с c... } finally { c.close() }`. Закрытие гарантировано даже если внутри блока вылетело исключение. Анти-паттерн — ручной `c?.close()` после цикла: если выше выскочит исключение, `close()` не позовётся.

Индексы колонок (`idCol`, `titleCol`, …) вычисляются **до** цикла. `getColumnIndexOrThrow` бросит `IllegalArgumentException`, если колонки нет в проекции — это правильное поведение (опечатка в `projection` упадёт здесь, а не где-то глубже). Если вычислять индексы внутри `while`, на 5000 треков получишь 5000 string-lookup'ов вместо одного.

`moveToNext()` сдвигает курсор на следующую строку и возвращает `true`, если она есть. `tracks +=` на `mutableListOf` — это `list.add(...)`.

Последнее выражение `tracks` — значение, которое вернёт `withContext`. Никакого `return@withContext` не нужно.

`.orFallback(...)` — наша вспомогательная extension, разберём её сразу.

Финал класса — два private helper'а:

```kotlin
actual class MusicScanner(private val context: Context) {

    actual suspend fun scanTracks(): List<Track> = withContext(Dispatchers.IO) { /* ... */ }

    private fun albumArtUri(albumId: Long): String =
        "content://media/external/audio/albumart/$albumId"

    private fun String?.orFallback(fallback: String): String =
        if (this.isNullOrBlank()) fallback else this
}
```

`albumArtUri` — формирует системный URI обложки альбома. Это легаси-схема, которая по-прежнему работает. На современных Android её можно заменить на `ContentUris.withAppendedId(...)`, но текущий вариант проще.

`String?.orFallback(...)` — много Kotlin-фишек в одной маленькой функции:

- **Extension-функция.** `fun String?.orFallback(...)` — «добавляю `orFallback` ко всем выражениям типа `String?`». Под капотом это статический метод, в который `this` передаётся первым параметром, но вызывать можно как метод: `someString.orFallback("...")`.
- **Receiver — `String?` (nullable).** Receiver сам может быть `null`. Внутри тела `this` имеет тип `String?`, не `String`. Поэтому работает и на `null`: `null.orFallback("X")` вернёт `"X"`, никакого NPE.
- **`isNullOrBlank()`** — стандартная extension в stdlib, тоже на `String?`. Возвращает `true` если `null`, пустая или из одних пробелов. Покрывает все «бесполезные» значения из MediaStore.
- **`else this`** — после `if (this.isNullOrBlank())` smart cast не сработает (это пользовательский предикат, не `if (this == null)`). Но компилятор разрешает вернуть `this`: тип возврата `String`, и компилятор понимает, что в этой ветке `this` уже не null и не blank.

### Шаг 6 — iOS-заглушка

Шаг технический: чтобы `commonMain` собирался для iOS-таргета, `actual class` нужен и там. Без него — ошибка компиляции «`expect MusicScanner` has no `actual` for iosMain». Реальную реализацию пишем не сейчас — на iOS гайд не нацелен. Кладём `TODO`-заглушку.

Создаём файл `MusicScanner.ios.kt`:

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

`TODO(...)` — это функция из stdlib, которая бросает `NotImplementedError`. Если кто-то попробует позвать `scanTracks()` на iOS-сборке — упадёт сразу с понятным сообщением, а не молча вернёт пустой список.

Конструктор без параметров — `iosMain` не имеет `Context` (это Android-специфичный класс). Подпись `expect class MusicScanner` это допускает: в `expect` мы не объявили primary constructor, поэтому каждая платформа решает сама. На Android он есть и принимает `Context`, на iOS — пустой.

### Шаг 7 — `TracksRepositoryImpl`

Реактивный слой поверх сканера: хранит текущий список треков в `MutableStateFlow`, перезаписывает его по `refresh()`. Главная мысль шага — «один источник правды для треков, никто другой их не пишет».

Создаём файл — пакет, импорты, объявление класса с конструктором (тело пока пустое):

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

    // дальше — поля _tracks (хранилище списка) и scanLock (защита от параллельных refresh), потом observeTracks() и refresh()
}
```

`MusicScanner` приходит через конструктор — стандартный DI. Кто конкретно его создаст (Android-`actual` с `Context`) — соберёт Koin на этапе 5 гайда.

Внутрь класса добавляем приватное состояние — `MutableStateFlow` со списком треков и `Mutex` для защиты от параллельных сканирований:

```kotlin
class TracksRepositoryImpl(
    private val scanner: MusicScanner,
) : TracksRepository {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val scanLock = Mutex()

    // дальше — observeTracks() для подписки на текущий список и refresh() для запуска нового сканирования
}
```

`MutableStateFlow<T>` — контейнер, который хранит **ровно одно текущее значение** типа `T` и одновременно является `Flow<T>`. Любой, кто подпишется, **сразу** получит текущее значение, а потом — каждое новое.

Сравни:
- Обычный `Flow<T>`: «холодная плёнка» — пока никто не вызвал `collect`, ничего не происходит. У него нет «текущего значения».
- `StateFlow<T>`: «горячее радио» — оно всегда что-то транслирует. Новый слушатель сразу слышит то, что играет прямо сейчас.

`emptyList()` — стартовое значение. Пока `refresh()` не позвали, экран увидит пустой список, а не зависнет в ожидании эмита.

Подчёркивание `_tracks` — соглашение Kotlin: «приватная мутабельная версия, наружу не показывать».

`Mutex` — очередь корутин. Внутри `withLock { ... }` может находиться **только одна** корутина одновременно. Остальные ждут.

Ключевое отличие от `synchronized(lock) { ... }`:

| | `synchronized` | `Mutex.withLock` |
|---|---|---|
| Что блокирует | **поток** — поток встаёт и ничего не делает | **корутину** — она приостанавливается (suspend), поток свободен брать другую работу |
| Откуда берётся | Java/JVM-примитив | Корутинный примитив (`kotlinx.coroutines.sync`) |
| Реентрант | Да (один поток может войти повторно) | **Нет** — повторный `withLock` из той же корутины = дедлок |
| Можно из `suspend` | Можно, но плохо: занимаем поток зря | Идиоматично |

Если бы стоял `synchronized`, на время `scanTracks()` (сотни миллисекунд на большой библиотеке) мы держали бы один из 64 потоков `Dispatchers.IO` намертво. С `Mutex` поток свободен.

Дальше — публичные методы. Сначала `observeTracks`:

```kotlin
class TracksRepositoryImpl(
    private val scanner: MusicScanner,
) : TracksRepository {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val scanLock = Mutex()

    override fun observeTracks(): Flow<List<Track>> = _tracks.asStateFlow()

    // дальше — refresh() для запуска нового сканирования через scanner.scanTracks() под Mutex'ом
}
```

`asStateFlow()` — это **апкаст** до `StateFlow<List<Track>>`. Возвращается тот же самый объект `_tracks`, но через тип без setter'а. Снаружи никто не сможет вызвать `_tracks.value = ...` — только подписаться и читать.

Зачем это — инкапсуляция: единственный способ положить туда новые треки — пройти через `refresh()`. Если бы мы отдавали `_tracks` напрямую, любой компонент мог бы перезаписать значение мимо `Mutex` и сломать инвариант «одно сканирование одновременно».

И финальный метод — `refresh`:

```kotlin
class TracksRepositoryImpl(
    private val scanner: MusicScanner,
) : TracksRepository {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val scanLock = Mutex()

    override fun observeTracks(): Flow<List<Track>> = _tracks.asStateFlow()

    override suspend fun refresh() {
        scanLock.withLock {
            _tracks.value = scanner.scanTracks()
        }
    }
}
```

Модификатор `suspend` — это **обещание**: «эта функция может приостановиться». Вызывать можно только из корутины (или из другой `suspend`-функции).

Что компилятор делает с `suspend`-функцией — добавляет ей скрытый параметр `Continuation<T>`, который описывает «куда вернуться после паузы». Когда корутина внутри `suspend` доходит до точки приостановки (`withContext(IO)`, `delay(...)`), она **не блокирует поток**: поток продолжает выполнять другие корутины, а наша «засыпает», запомнив свой `Continuation`. Когда событие произошло — рантайм возобновляет корутину, возможно уже на другом потоке.

Это и есть «корутина приостановилась»: не «поток встал в `Thread.sleep`», а «функция запомнила место и отдала поток обратно».

`withLock` — это `lock()` + `try { блок } finally { unlock() }`. Никакой магии: блокируется на входе, освобождается на выходе **даже при исключении**. Если корутину отменят прямо во время `scanTracks()` — лок всё равно отдадут.

Сценарий, ради которого `Mutex` и стоит: пользователь дважды нажал «Обновить» подряд.

1. Первый клик — корутина A: входит в `withLock`, блокирует mutex, начинает `scanTracks()` (suspend, «спит»).
2. Через 50 мс — второй клик — корутина B: входит в `refresh()`, доходит до `withLock` — mutex занят, корутина B приостанавливается.
3. Корутина A досканировала, присвоила `_tracks.value = ...`, вышла из `withLock`, отпустила mutex.
4. Корутина B автоматически просыпается, входит в `withLock`, начинает свой `scanTracks()`.

Без `Mutex` сценарий был бы: A и B сканируют одновременно, обе пишут в `_tracks.value`, кто пишет последним — тот и победил. На MediaStore это не критично (запросы независимые), но уже на уровне БД или сети — гарантированный race.

Присваивание `.value` атомарно публикует новое значение всем подписчикам. С двумя оговорками:

- **Conflated.** Если подписчик ещё не успел обработать предыдущее значение, а пришло новое — он увидит только новое, промежуточное «потеряется». Для UI-стейта это нормально: показываем **последний** список, а не каждый промежуточный.
- **`distinctUntilChanged` встроено.** Если новое значение `equals` старому — подписчики не получат повторный эмит. Поэтому `data class Track(...)` важен (у него правильный `equals`): если список после нового сканирования совпал с предыдущим — UI не будет зря перерисовываться.

### Шаг 8 — `AlbumsRepositoryImpl` (группировка)

Главный пример «делать почти ничего, но получать реактивность бесплатно». Альбомы нигде не хранятся в поле — они **выводятся** из текущего списка треков на лету через `Flow.map`.

Создаём файл — пакет, импорты, объявление класса с конструктором:

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

    // дальше — observeAlbums() для списка альбомов, observeTracksOfAlbum() для треков альбома, private groupIntoAlbums() для самой группировки
}
```

`tracksRepository` — единственная зависимость: всё нужное мы выведем из его потока треков.

Первый публичный метод — `observeAlbums`:

```kotlin
class AlbumsRepositoryImpl(
    private val tracksRepository: TracksRepository,
) : AlbumsRepository {

    override fun observeAlbums(): Flow<List<Album>> =
        tracksRepository.observeTracks().map { tracks -> groupIntoAlbums(tracks) }

    // дальше — observeTracksOfAlbum() для треков альбома и private groupIntoAlbums() для группировки
}
```

В одной строке два важных момента.

**`observeTracks()`** возвращает `Flow<List<Track>>` — «провод», по которому каждый раз, когда `TracksRepositoryImpl` обновляет `_tracks.value`, прилетает свежий список.

**`.map { ... }`** — это **`Flow.map`** из `kotlinx.coroutines.flow`, оператор потока. Он вешается на провод: «когда прилетит `tracks`, прогони через эту функцию и эмитни результат дальше». `groupIntoAlbums` ничего не подписывает — она вызовется **на каждый emit upstream**.

Важно не путать этот `Flow.map` с другим `.map`, который встретится дальше (`items.map { it.artist }`). Имена одинаковые, но **разные функции с разной семантикой**:

| | `Flow.map` (тут) | `List.map` (дальше) |
|---|---|---|
| Receiver | `Flow<T>` | `List<T>` |
| Когда выполняется | На каждый emit upstream-flow | Один раз, синхронно |
| Возвращает | Новый `Flow<R>` | Новый `List<R>` |
| Импорт | `kotlinx.coroutines.flow.map` | `kotlin.collections` (даже импорт не нужен) |

Одинаковое имя — совпадение API: и потоки, и коллекции естественно поддерживают «трансформацию каждого элемента». Под капотом — разный код.

Второй публичный метод — треки конкретного альбома:

```kotlin
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

    // дальше — private fun groupIntoAlbums(), для группировки List<Track> в List<Album> по albumId
}
```

Та же `Flow.map` снаружи, а внутри — стандартные `List.filter` и `List.sortedBy` (синхронные). Сначала отфильтровали треки этого альбома, потом отсортировали по названию.

Теперь — private-функция `groupIntoAlbums`, ради которой всё затевалось. Цепочка из трёх вызовов на `List<Track>`. Начнём с `groupBy`:

```kotlin
    private fun groupIntoAlbums(tracks: List<Track>): List<Album> =
        tracks
            .groupBy { it.albumId }
            // дальше — .map { ... } для превращения каждой группы в Album и .sortedBy { ... } для сортировки результата
```

`groupBy` возвращает `Map<String, List<Track>>`. Ключ — `albumId` каждого трека, значение — список всех треков с этим `albumId`.

Конкретный пример. На устройстве 5 треков:

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

Гарантия: пустых групп `groupBy` **не создаёт**. В каждой паре «ключ → список» список содержит как минимум один элемент. Пригодится дальше — можем спокойно звать `items.first()`.

Превращаем каждую группу в `Album`:

```kotlin
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
            // дальше — .sortedBy { it.title.lowercase() } для сортировки альбомов по названию без учёта регистра
```

Это **`Map.map { entry -> ... }`** из stdlib (НЕ `Flow.map`!). На входе — `Map.Entry<String, List<Track>>`, на выходе — `List<Album>`.

`(albumId, items)` — это **деструктуризация**. У `Map.Entry` есть `componentN`-функции, которые позволяют записать `entry.key` и `entry.value` короче. Без деструктуризации это выглядело бы так:

```kotlin
.map { entry ->
    val albumId = entry.key
    val items = entry.value
    Album(...)
}
```

Внутри лямбды по строкам:

`val artists = items.map { it.artist }.distinct()` — здесь `.map { it.artist }` это **`List.map`** (синхронный): из списка треков сделали список имён исполнителей. `.distinct()` оставляет только уникальные значения, сохраняя порядок (использует `LinkedHashSet` под капотом). Зачем — альбом может содержать треки разных исполнителей (сборники, фиты), и нам нужно знать, все ли исполнители одинаковые.

`val artist = if (artists.size == 1) artists.first() else "Various Artists"` — если все треки от одного исполнителя, пишем его имя; иначе — стандартная пометка «Various Artists» (так делают все плееры).

`title = items.first().album` — берём название альбома из первого трека группы. Безопасно, потому что `groupBy` гарантирует непустые списки. Если бы не были уверены — пришлось бы `items.firstOrNull()?.album ?: "Без названия"`. Зачем у всех треков альбома название одинаковое: MediaStore сам это обеспечивает — `albumId` и есть хеш названия + исполнителя.

`coverUri = items.firstOrNull { it.coverUri != null }?.coverUri` — хитрая деталь. Берём **первый** трек, у которого `coverUri != null`, а не просто `items.first().coverUri`. Сценарий: альбом из 10 треков, у первых трёх MediaStore не нашёл обложку (`null`), а у четвёртого нашёл. С `items.first().coverUri` мы бы получили `null` и показали плейсхолдер. С `firstOrNull { it.coverUri != null }` — найдём ту обложку, что есть.
- `firstOrNull { предикат }` — возвращает первый элемент, удовлетворяющий предикату, или `null`.
- `?.coverUri` — safe call: если результат не null, прочитай его `coverUri`; иначе оставь `null`.

`totalDurationMs = items.sumOf { it.duration }` — `sumOf` это стандартная функция-агрегат. Прогоняет лямбду по каждому элементу, складывает результаты. Эквивалентно `items.map { it.duration }.sum()`, но без промежуточного списка. Получаем суммарную длительность альбома в миллисекундах — пригодится для «42 трека • 3 ч 12 мин».

Финал пайплайна — сортировка результата:

```kotlin
    private fun groupIntoAlbums(tracks: List<Track>): List<Album> =
        tracks
            .groupBy { it.albumId }
            .map { (albumId, items) -> Album( /* ... */ ) }
            .sortedBy { it.title.lowercase() }
```

Сортировка списка `Album`-ов по названию, **с приведением к нижнему регистру**.

Без `.lowercase()` сравнение шло бы по кодпойнтам Unicode напрямую. А там:

| Символ | Кодпойнт |
|---|---|
| `A`–`Z` | 65–90 |
| `a`–`z` | 97–122 |
| `А`–`Я` | 1040–1071 |
| `а`–`я` | 1072–1103 |

То есть **`Z` (90) < `a` (97) < `Я` (1071) < `я` (1103)**. Без `.lowercase()`:
- Альбом `"banana"` шёл бы **после** `"Apple"` (потому что `b` > `A`), но случайно: `b` (98) > `A` (65). Для `"apple"` и `"Banana"` вышло бы наоборот.
- Кириллица улетела бы в самый конец, **после** всей латиницы.
- А `"яблоко"` оказалось бы после `"Я"` (потому что `я` > `Я`).

С `.lowercase()` сортировка чувствительна только к буквам, не к регистру. `"Apple"` и `"apple"` встанут рядом, кириллица перемешается с латиницей по алфавиту.

> **Замечание для перфекциониста.** `lowercase()` без локали работает не идеально для тонких случаев — турецкая `İ` → `i̇` (с точкой), немецкая `ß` → `ss`. Идеально правильная сортировка — через `Collator` (JVM) или ICU, но это несоразмерно сложнее. Для MVP `.lowercase()` — компромисс «в 100 раз лучше, чем без него».

### Шаг 9 — Permission в манифесте

Манифест — это «декларация намерений» приложения для системы. Чтобы запросить permission в рантайме (Шаг 10), сначала надо объявить его здесь — иначе рантайм-запрос будет молча отклонён.

Открываем `AndroidManifest.xml` и добавляем два `<uses-permission>` внутрь `<manifest>` (перед `<application>`):

```xml
<!-- composeApp/src/androidMain/AndroidManifest.xml -->
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

Тут два разных permission'а, потому что в Android 13 Google разделил доступ к медиа по типам: фото, видео, аудио — отдельные permission'ы. До 13 был один общий `READ_EXTERNAL_STORAGE`.

`maxSdkVersion="32"` означает: на Android 13+ это разрешение даже не запрашивается, там работает `READ_MEDIA_AUDIO`. Без этого ограничения на новых устройствах система могла бы показать пользователю «приложение хочет доступ к файлам», что и пугает, и не работает (даст storage, но не аудио).

### Шаг 10 — Compose-обёртка для запроса разрешения

Activity Result API — современная замена `onActivityResult`. Идея: «я хочу запустить системный экран и получить результат, при этом не зависеть от lifecycle Activity вручную». Compose-обёртка `rememberLauncherForActivityResult` делает это идиоматично из любого `@Composable`.

Создаём файл — пакет, импорты, тип состояния, сигнатура функции и первый блок внутри (выбор имени permission'а по версии Android):

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

    // дальше внутри функции — state (текущий статус permission'а), launcher (запускалка системного диалога), request (стабильный триггер) и return пары
}
```

`enum class AudioPermissionState` — почему `enum`, а не `sealed interface`. У нас три **константных** состояния, каждое без собственных данных. Если бы понадобилось «`Denied(reason: String)`» — пришлось бы переключаться на `sealed interface`, потому что у `enum` нет полей-переменных.

`LocalContext.current` — это `CompositionLocal`, механизм Compose, который позволяет передать значение «вниз по дереву композиций» без явной передачи через параметры. Корневой `setContent { ... }` положил туда текущий `Context`, а любой Composable ниже может его взять через `.current`. Думай об этом как о неявной переменной в области видимости: «во всём поддереве — этот Context».

Выбор имени permission'а от версии Android. Android не даёт одну универсальную константу для «читать аудио». На API 33+ это `READ_MEDIA_AUDIO`, раньше — `READ_EXTERNAL_STORAGE`. `Build.VERSION_CODES.TIRAMISU` — это просто `33`, константа из SDK. Имена-кодовые («Tiramisu», «UpsideDownCake») выпускаются вместе с релизом Android: 33 = Tiramisu = Android 13.

Что было бы без проверки: попытка запросить `READ_MEDIA_AUDIO` на Android 12 (API 32) — система не знает такого permission. А `READ_EXTERNAL_STORAGE` на Android 13+ перестал давать доступ к аудио — Google разделил пермишены по типам медиа. Поэтому строго: 33+ = `READ_MEDIA_AUDIO`, ниже = `READ_EXTERNAL_STORAGE`. Манифест и runtime-проверка должны совпадать.

Теперь внутрь функции добавляем переменную состояния `state` со стартовым значением — проверяем, не выдан ли уже permission:

```kotlin
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

    // дальше — launcher для системного диалога разрешений, request как стабильный триггер и return пары (state, request)
}
```

В строке `var state by remember { mutableStateOf(...) }` три отдельных механизма, которые часто путают.

**`mutableStateOf(value)`** создаёт `MutableState<T>` — наблюдаемый «контейнер» с одним полем `.value`. Когда `.value` меняется, Compose замечает это и **перерисовывает** все Composable-функции, которые читали `.value`. Это и есть «состояние, которое видит Compose».

**`remember { ... }`** — это «удержать значение между рекомпозициями». Сама `rememberAudioPermissionState()` будет вызвана много раз (при каждой рекомпозиции экрана), но `remember` запоминает результат лямбды **в первый раз** и при последующих вызовах возвращает тот же объект. Без `remember` мы создавали бы новый `MutableState` каждый раз, теряя предыдущее значение.

**`var ... by ...`** — это **property delegation**. Когда пишешь `var state by mutableState`, компилятор подставляет:
- `state` (чтение) → `mutableState.getValue(this, ::state)` → `mutableState.value`
- `state = ...` (запись) → `mutableState.setValue(this, ::state, ...)` → `mutableState.value = ...`

То есть `state` ведёт себя как обычная переменная, но под капотом каждое чтение/запись ходит в `MutableState.value`. Синтаксический сахар, чтобы не писать `state.value` каждый раз.

Внутри `mutableStateOf(...)` инициализируем стартовое значение: если permission уже выдан (это бывает на повторных запусках) — `Granted`, иначе `Unknown` (ещё не спросили). `ContextCompat.checkSelfPermission` — синхронная проверка, без диалогов; просто «есть/нет прямо сейчас».

Теперь регистрируем launcher — объект, который умеет показать системный диалог разрешений и принять результат:

```kotlin
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

    // дальше — request (стабильная функция-триггер для вызова launcher.launch) и return пары (state, request) наружу
}
```

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

`contract = ActivityResultContracts.RequestPermission()` — это «шаблон взаимодействия»: вход — `String` (имя permission), выход — `Boolean` (granted). Контракты есть и на другие сценарии: `PickVisualMedia`, `TakePicture`, `OpenDocument`.

Финальная часть функции — стабильная функция-триггер `request` и возврат пары наружу:

```kotlin
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

В строке `val request = remember(launcher) { { launcher.launch(permission) } }` двойные фигурные в одну строку — две разных конструкции одна в одной.

Внешние скобки — лямбда, которую `remember` запоминает (её провайдер начального значения).
Внутренние скобки — лямбда, которую мы хотим **сохранить как значение**: `() -> Unit`, которая при вызове запустит permission request.

Развёрнуто это выглядит так:

```kotlin
val request: () -> Unit = remember(launcher) {
    val capturedLauncher = launcher
    val capturedPermission = permission
    return@remember { capturedLauncher.launch(capturedPermission) }
}
```

`remember(launcher)` с **ключом** означает: «запомни значение, пока ключ `launcher` не поменялся». Если `launcher` пересоздастся (теоретически — после Activity recreate) — лямбда `request` пересоздастся с новым launcher внутри.

Зачем это вообще: если бы мы передавали `{ launcher.launch(permission) }` напрямую в дочерний Composable, Compose видел бы «новую лямбду на каждой рекомпозиции» и считал бы дочерние Composable нестабильными → лишние рекомпозиции. С `remember` лямбда стабильная — ссылка одна и та же.

`return state to request` — `a to b` это инфиксная функция-фабрика для `Pair<A, B>`. Эквивалентно `Pair(state, request)`. В вызывающем коде раскладывается обратно через деструктуризацию: `val (permissionState, requestPermission) = rememberAudioPermissionState()`. Та же механика, что у деструктуризации `Map.Entry` — у `Pair` тоже есть `component1`/`component2`.

Использование на экране (подробно `TracksScreen` разберём в этапе 7, здесь — чтобы увидеть полную цепочку):

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

Composable получает текущее состояние и функцию-триггер; `LaunchedEffect` стартует загрузку при `Granted`; `when` показывает один из трёх UI в зависимости от состояния.

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
