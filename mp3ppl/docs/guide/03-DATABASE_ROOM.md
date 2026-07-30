# 03. База данных — Room для пользовательских альбомов

## Зачем

Альбомы из метаданных мы получаем бесплатно из MediaStore. Но **пользовательские альбомы** (они же "мои плейлисты") надо где-то хранить — их нет ни в какой системной БД.

Требования к хранилищу:
- Сохраняется между запусками.
- Связь "один альбом — много треков" и "один трек может быть в нескольких альбомах" → **many-to-many**.
- Реактивные обновления: добавил трек в альбом → экран альбома обновился автоматически.
- Работает быстро — 10000 треков не должны тормозить.

Под эти требования идеально подходит **Room**: обёртка над SQLite, которая генерирует реализацию DAO по аннотациям + умеет возвращать `Flow<...>`, которые сами эмитят при изменении таблиц.

---

## Что реализуем

1. Подключим Room + KSP к `shared/data`.
2. Создадим entity `UserAlbumEntity` и ассоциативную таблицу `UserAlbumTrackCrossRef`.
3. Сделаем DAO с `Flow<List<UserAlbumWithTracks>>` через `@Transaction` + `@Relation`.
4. Создадим `AppDatabase` с миграциями.
5. Напишем `UserAlbumsRepositoryImpl`, который мапит entity → доменные модели.
6. Добавим доменный интерфейс `UserAlbumsRepository`.

Новые файлы:

```
shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/
├── model/UserAlbum.kt                      (новый, доменная модель)
└── repository/UserAlbumsRepository.kt      (новый, интерфейс)

shared/data/src/androidMain/kotlin/org/example/mp3player/data/
├── database/
│   ├── AppDatabase.kt                      (новый, Room DB)
│   ├── entities/
│   │   ├── UserAlbumEntity.kt              (новый)
│   │   ├── UserAlbumTrackCrossRef.kt       (новый)
│   │   └── UserAlbumWithTrackIds.kt        (новый)
│   └── dao/
│       └── UserAlbumsDao.kt                (новый)
└── repository/
    └── UserAlbumsRepositoryImpl.kt         (новый)
```

Почему всё под `androidMain`? Room на Android работает отлично. **Room 2.7+** поддерживает KMP, но с ограничениями; для учебного проекта проще положить всё под `androidMain` и опубликовать через `expect/actual` только **интерфейс**, если на iOS будет другая БД (например, SQLDelight).

---

## Реализация

### Шаг 1 — Добавить зависимости

Room — это не просто библиотека, а **библиотека + кодогенератор**. Аннотации `@Entity`, `@Dao`, `@Query` сами по себе ничего не делают: их читает **KSP-процессор Room** во время сборки и генерирует реализации DAO. Поэтому в `gradle` подключаются две вещи: runtime (`room-runtime`) и compiler (`room-compiler`) — последний работает через KSP.

Добавляем версии и артефакты в version catalog:

```toml
# gradle/libs.versions.toml
[versions]
room = "2.8.4"
sqlite = "2.6.2"
ksp = "2.3.0"

[libraries]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-sqlite-bundled = { group = "androidx.sqlite", name = "sqlite-bundled", version.ref = "sqlite" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidx-room = { id = "androidx.room", version.ref = "room" }
```

`room-runtime` — само API Room (классы `RoomDatabase`, `Room.databaseBuilder`). `room-ktx` — расширения для корутин и Flow. `room-compiler` — процессор, генерирующий `_Impl` классы.

Подключаем плагин KSP и зависимости в модуль `data`:

```kotlin
// shared/data/build.gradle.kts
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidx.room)          // ← Room Gradle Plugin: конфигурирует схемы
    alias(libs.plugins.ksp)                    // ← генератор кода
}

kotlin {
    // ...
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.room.ktx)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
}
```

`alias(libs.plugins.androidx.room)` — **Room Gradle Plugin** (артефакт `androidx.room`, id `androidx.room`). Он нужен ради блока `room { schemaDirectory(...) }`: без плагина `exportSchema = true` в `@Database` потребовал бы вручную прописывать `ksp { arg("room.schemaLocation", ...) }` для каждого таргета. С плагином — одна строка.

Схемы (JSON-снимки структуры БД) складываются в `shared/data/schemas/` и **коммитятся в git**: по ним пишутся и тестируются миграции (Шаг 9).

Что физически происходит при `./gradlew :shared:data:assembleDebug`:

1. Kotlin-компилятор парсит твои файлы.
2. KSP запускает зарегистрированные процессоры. У Room процессор зарегистрирован через `add("kspAndroid", libs.androidx.room.compiler)`.
3. Процессор Room обходит классы, помеченные `@Database`, `@Dao`, `@Entity`. Для каждого `@Dao` он **генерирует Kotlin-файл с реализацией**: например, для `UserAlbumsDao` будет создан `UserAlbumsDao_Impl`.
4. Сгенерированные файлы лежат в `build/generated/ksp/<sourceSet>/kotlin/` — IDE их подхватывает, можно открыть и посмотреть глазами (полезно для отладки «что Room делает с моим запросом»).
5. Дальше эти файлы тоже компилируются и попадают в APK.

То есть `@Dao interface UserAlbumsDao` — это **«заявка»**, а реальный класс с реализацией — `UserAlbumsDao_Impl`. Когда ты вызываешь `db.userAlbumsDao()`, возвращается экземпляр `_Impl`-класса.

**Почему именно `add("kspAndroid", ...)`, а не просто `ksp(...)`.** В KMP-модуле таргетов несколько (`androidTarget`, `iosX64`, `iosArm64`...), и для каждого Gradle создаёт свою configuration: `kspAndroid`, `kspIosX64`, и т.д. `add("kspAndroid", libs.androidx.room.compiler)` говорит: «прогоняй Room-процессор только в Android-таргете». Это правильно — Room работает только под JVM/Android, попытка запустить его на iOS-таргете провалится. Если бы написали `ksp(...)` без префикса — Gradle не знал бы, к какому таргету привязать процессор.

Без правильного `add(...)` получишь при сборке: `Cannot find implementation for ... AppDatabase. AppDatabase_Impl does not exist`. Это значит, что процессор не запустился и `_Impl`-классов нет.

### Шаг 2 — Доменная модель

Сначала описываем форму данных в domain — чистый Kotlin, без Android и Room. Создаём `UserAlbum.kt`:

```kotlin
// shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/model/UserAlbum.kt
package org.example.mp3player.domain.model

data class UserAlbum(
    val id: Long,
    val title: String,
    val description: String,
    val coverUri: String?,
    val createdAt: Long,       // epoch millis
    val trackIds: List<String>,
)
```

**Почему `trackIds: List<String>`, а не `tracks: List<AudioTrack>`?** Потому что `UserAlbum` не должен знать о том, где лежат треки. Список id — это просто ссылки. Presentation-слой потом сам «подтянет» треки по id через `TracksRepository`. Это развязывает слои: если завтра треки начнут приходить из сети, `UserAlbum` не изменится.

### Шаг 3 — Интерфейс репозитория

Контракт на работу с пользовательскими альбомами — куда обращается ViewModel, без знания о Room:

```kotlin
// shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/repository/UserAlbumsRepository.kt
package org.example.mp3player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.example.mp3player.domain.model.UserAlbum

interface UserAlbumsRepository {
    fun observeAll(): Flow<List<UserAlbum>>
    fun observeById(id: Long): Flow<UserAlbum?>

    suspend fun create(title: String, description: String, coverUri: String?): Long
    suspend fun rename(id: Long, newTitle: String)
    suspend fun setCover(id: Long, coverUri: String?)
    suspend fun delete(id: Long)

    suspend fun addTrack(albumId: Long, trackId: String)
    suspend fun removeTrack(albumId: Long, trackId: String)
    suspend fun reorderTracks(albumId: Long, trackIds: List<String>)
}
```

Реактивные методы (`observe*`) возвращают `Flow` — подписчик получает обновления автоматически при изменении БД. Мутирующие (`create`/`rename`/...) — `suspend`, потому что под капотом будут SQL-запросы, которые блокируют поток.

`create()` возвращает `Long` — id новой записи. Это удобно: создал альбом → сразу можешь добавить в него треки по id.

### Шаг 4 — Room Entity

Entity — это data class, помеченный `@Entity`. Room возьмёт его и создаст из него таблицу: каждое `val` поле без аннотаций станет колонкой. Нам нужны две таблицы:

1. `user_albums` — сами альбомы.
2. `user_album_track_cross_ref` — связь many-to-many: какой трек в каком альбоме и в какой позиции.

Начнём с альбома:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/database/entities/UserAlbumEntity.kt
package org.example.mp3player.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_albums")
data class UserAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val coverUri: String? = null,
    val createdAt: Long,
)
```

`@Entity(tableName = "user_albums")` — явное имя таблицы. Без него Room взял бы имя класса (`UserAlbumEntity` → таблица `UserAlbumEntity`), что не идиоматично для SQL.

`@PrimaryKey(autoGenerate = true) val id: Long = 0` — идиома **«Room сам выдаст id при insert»**. Работает так:
- При `dao.insertAlbum(UserAlbumEntity(title = "...", createdAt = ...))` ты не передаёшь `id`. Дефолтное `0` означает «новая запись».
- Room сгенерирует SQL `INSERT INTO user_albums (id, title, ...) VALUES (NULL, ?, ...)`. Передача `NULL` в `INTEGER PRIMARY KEY AUTOINCREMENT` — сигнал SQLite сгенерировать следующий свободный id.
- `@Insert`-метод вернёт `Long` — этот сгенерированный id, который ты дальше используешь.

Если случайно передать ненулевой id (`UserAlbumEntity(id = 42, ...)`):
- При `OnConflictStrategy.ABORT` (дефолт) и существующем `id=42` — `SQLiteConstraintException`.
- При `REPLACE` — старая строка удалится, новая встанет на её место.
- При `IGNORE` — insert тихо ничего не сделает, вернёт `-1`.

Теперь связующая таблица — кто-где-в-каком-порядке:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/database/entities/UserAlbumTrackCrossRef.kt
package org.example.mp3player.data.database.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "user_album_track_cross_ref",
    primaryKeys = ["albumId", "trackId"],
    indices = [Index("albumId"), Index("trackId")],
)
data class UserAlbumTrackCrossRef(
    val albumId: Long,
    val trackId: String,
    val position: Int,       // порядок в альбоме
)
```

**Составной первичный ключ `primaryKeys = ["albumId", "trackId"]`.** Уникальной должна быть **пара** `(albumId, trackId)`, а не каждое поле по отдельности. SQLite сам обеспечивает: попытка вставить вторую строку с такой же парой → `UNIQUE constraint failed`. То есть один и тот же трек не может быть в одном альбоме дважды — БД это гарантирует.

Альтернативный дизайн: добавить отдельный `@PrimaryKey id: Long = 0` (auto-generated) + UNIQUE INDEX на пару. Работает, но избыточно — лишняя колонка, лишний индекс. Составной PK прямее: «связь определяется парой».

**Индексы — что физически.** Без индекса запрос `SELECT * FROM user_album_track_cross_ref WHERE albumId = 42` идёт **полным сканом**: SQLite читает каждую строку и сравнивает `albumId` с 42. Сложность O(N).

С индексом SQLite поддерживает отдельную **B-tree-структуру**, в которой ключи (`albumId`) отсортированы. Поиск по B-tree — O(log N). На таблице из 100 000 строк это разница примерно в 10 000 раз.

Цена: на каждый `INSERT/UPDATE/DELETE` индекс надо обновлять. Поэтому индексы ставят только на колонки, по которым **действительно фильтруют**: у нас это `albumId` (запросы «треки этого альбома») и `trackId` (запросы «в каких альбомах этот трек»).

Не индексируй колонки, которые не используются в `WHERE`/`JOIN`/`ORDER BY` — это пустая трата места и времени.

### Шаг 5 — Связка для загрузки альбома со списком trackIds

Room умеет загружать связанные данные одним запросом через `@Relation`. Для этого нужен промежуточный класс, который опишет связь — Room сам сгенерирует JOIN.

Создаём класс-связку:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/database/entities/UserAlbumWithTrackIds.kt
package org.example.mp3player.data.database.entities

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Альбом со списком trackId через junction-таблицу.
 * Room сам сгенерирует JOIN по полям.
 */
data class UserAlbumWithTrackIds(
    @Embedded val album: UserAlbumEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "trackId",
        associateBy = Junction(
            value = UserAlbumTrackCrossRef::class,
            parentColumn = "albumId",
            entityColumn = "trackId",
        ),
    )
    val refs: List<UserAlbumTrackCrossRef>,
) {
    /** Упорядоченный список trackId. */
    val orderedTrackIds: List<String>
        get() = refs.sortedBy { it.position }.map { it.trackId }
}
```

Здесь Room делает **два SQL-запроса** и сшивает результаты в одну Kotlin-структуру.

**`@Embedded val album: UserAlbumEntity`** — поля entity «вплавляются» прямо в этот класс. На уровне SQL это просто `SELECT * FROM user_albums` — все колонки `UserAlbumEntity` лягут в `album`.

**`@Relation(...)`** — описание «как найти связанные записи». Два пути:

1. **Прямая связь.** Если бы `UserAlbumTrackCrossRef` имел поле, ссылающееся прямо на родителя (`val parentAlbumId: Long`), хватило бы `@Relation(parentColumn = "id", entityColumn = "parentAlbumId")` — Room сам сделал бы `SELECT * FROM cross_ref WHERE parentAlbumId = ?`.

2. **Через junction-таблицу (наш случай).** Связь many-to-many не выражается одной колонкой. Junction — отдельная таблица-склейка с двумя FK. `Junction(value = UserAlbumTrackCrossRef::class, parentColumn = "albumId", entityColumn = "trackId")` говорит: «найди записи в `UserAlbumTrackCrossRef` через `albumId`, потом по `trackId` достань цели». В нашем случае «цели» — это сами `UserAlbumTrackCrossRef` (мы вытаскиваем junction-записи, не пытаемся проникнуть в `AudioTrack`-таблицу — её и нет, треки в MediaStore).

В рантайме Room делает примерно так:

```sql
-- Запрос 1: родители
SELECT * FROM user_albums ORDER BY createdAt DESC;

-- Запрос 2: для каждого родителя — junction-записи
SELECT * FROM user_album_track_cross_ref WHERE albumId IN (1, 2, 3, ...);
```

Дальше Room в Kotlin-коде группирует результат по `albumId` и собирает `List<UserAlbumWithTrackIds>`.

`orderedTrackIds` — computed property: `refs` от Room прилетают в произвольном порядке (зависит от индекса), мы сортируем по `position` и отдаём наружу только id. Логика «как упорядочить» спрятана здесь, наружу торчит только готовый `List<String>`.

### Шаг 6 — DAO

DAO — это интерфейс с SQL-запросами, помеченный `@Dao`. Room сгенерирует под него `_Impl`-класс с реальными вызовами SQLite. Соберём интерфейс по группам методов: сначала reactive observe (с `@Transaction`), потом CRUD по альбомам, потом по cross-ref, и в конце транзакционный `reorderTracks`.

Создаём файл — пакет, импорты, объявление интерфейса:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/database/dao/UserAlbumsDao.kt
package org.example.mp3player.data.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.example.mp3player.data.database.entities.UserAlbumEntity
import org.example.mp3player.data.database.entities.UserAlbumTrackCrossRef
import org.example.mp3player.data.database.entities.UserAlbumWithTrackIds

@Dao
interface UserAlbumsDao {

    // дальше — observe-методы (Flow + @Transaction для @Relation), CRUD по альбомам, CRUD по cross-ref, и @Transaction reorderTracks
}
```

Добавляем реактивные observe — они вернут `Flow`, который сам переэмитит при изменении таблиц:

```kotlin
@Dao
interface UserAlbumsDao {

    @Transaction
    @Query("SELECT * FROM user_albums ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UserAlbumWithTrackIds>>

    @Transaction
    @Query("SELECT * FROM user_albums WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<UserAlbumWithTrackIds?>

    // дальше — CRUD по альбомам, CRUD по cross-ref, и @Transaction reorderTracks
}
```

**`Flow<List<X>>` в DAO — как работает invalidation tracker.** Когда метод DAO возвращает `Flow<...>`, генератор делает не «один раз выполнить запрос», а **подписку**:

1. Room держит `InvalidationTracker` — внутренний компонент, который слушает все `INSERT/UPDATE/DELETE` через SQLite-триггеры.
2. На этапе генерации `_Impl` Room анализирует SQL запроса и понимает, на какие таблицы он смотрит. Здесь — `user_albums` плюс (из-за `@Transaction` + `@Relation`) `user_album_track_cross_ref`.
3. `Flow` подписывается на изменения этих таблиц.
4. При любом write в наблюдаемые таблицы tracker помечает запрос «грязным» и эмитит новое значение в `Flow` — фактически перезапускает SQL-запрос и шлёт результат.

То есть «магия» имеет конкретный механизм: триггеры в SQLite + tracker в Java + конвертация в Flow. Можно посмотреть в `build/generated/ksp/...` сгенерированный код — там это всё явно.

Подвох: tracker инвалидирует **по таблице целиком**, не по конкретным строкам. Если ты пишешь в `user_albums` 1000 раз подряд — Flow эмитнет 1000 раз. Решение — батч в одной `@Transaction`-функции (одна транзакция = один эмит).

**Зачем `@Transaction` именно с `@Relation`.** Метод `observeAll` под капотом — два SQL-запроса: «выбрать альбомы» + «выбрать junction-записи для этих альбомов». Между ними проходит немного времени, но достаточно, чтобы кто-то параллельно успел вставить/удалить строки. Без `@Transaction` на руках окажется Kotlin-структура, где альбомы и их связи не согласованы. `@Transaction` оборачивает обе SQL-команды в один транзакционный блок: SQLite держит read-snapshot, никакие внешние изменения не повлияют. Без `@Transaction` Room выдаст compile-warning, но не ошибку — поэтому легко забыть. Привычка: `@Relation` ⇒ всегда `@Transaction`.

Дальше — CRUD по альбомам (вставка, обновление, удаление, плюс утилитный `maxPosition`):

```kotlin
@Dao
interface UserAlbumsDao {

    @Transaction
    @Query("SELECT * FROM user_albums ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UserAlbumWithTrackIds>>

    @Transaction
    @Query("SELECT * FROM user_albums WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<UserAlbumWithTrackIds?>

    @Insert
    suspend fun insertAlbum(album: UserAlbumEntity): Long

    @Update
    suspend fun updateAlbum(album: UserAlbumEntity)

    @Query("DELETE FROM user_albums WHERE id = :id")
    suspend fun deleteAlbum(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM user_album_track_cross_ref WHERE albumId = :albumId")
    suspend fun maxPosition(albumId: Long): Int

    // дальше — CRUD по cross-ref (insertCrossRef с IGNORE, removeCrossRef, removeAllCrossRefs, insertCrossRefs) и @Transaction reorderTracks
}
```

`@Insert` возвращает `Long` — это id новой записи (см. Шаг 4 про `autoGenerate`). `@Update` принимает entity и обновляет строку по PK. `@Query("DELETE ...")` — явный SQL.

`maxPosition` нужен, чтобы при добавлении трека в альбом дать ему позицию `max + 1` (треки в конец). `COALESCE(MAX(position), -1)` — если строк ещё нет, `MAX` вернёт `NULL`, `COALESCE` подменит его на `-1`. Тогда первый трек получит позицию `-1 + 1 = 0`.

**`suspend` функции в DAO — какой диспатчер.** Room автоматически выполняет `suspend`-методы DAO **не на Main**. Куда именно — определяется при создании БД, дефолт — внутренний пул потоков Room (`ArchTaskExecutor`). Это не `Dispatchers.IO`, но логически близко: фоновый пул для I/O. Для `suspend`-метода Room делает примерно `withContext(roomQueryDispatcher) { ...запрос... }` — поэтому вызывать DAO из `viewModelScope.launch` (который по дефолту на Main) безопасно.

Если был бы не-`suspend` метод, возвращающий значение напрямую (синхронный SQL-запрос), Room выкинул бы `IllegalStateException: Cannot access database on the main thread`. Намеренная защита от UI-фриза.

Теперь — операции с cross-ref:

```kotlin
@Dao
interface UserAlbumsDao {

    // ... observe* выше ...
    // ... CRUD по альбомам выше ...

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: UserAlbumTrackCrossRef)

    @Query("DELETE FROM user_album_track_cross_ref WHERE albumId = :albumId AND trackId = :trackId")
    suspend fun removeCrossRef(albumId: Long, trackId: String)

    @Query("DELETE FROM user_album_track_cross_ref WHERE albumId = :albumId")
    suspend fun removeAllCrossRefs(albumId: Long)

    @Insert
    suspend fun insertCrossRefs(refs: List<UserAlbumTrackCrossRef>)

    // дальше — @Transaction reorderTracks (стереть все cross-ref альбома и пересоздать в новом порядке)
}
```

**`OnConflictStrategy.IGNORE` vs `REPLACE` vs `ABORT`** — настройка для `INSERT INTO ...`:

| Стратегия | SQL | Что делает при конфликте PK/UNIQUE |
|---|---|---|
| `ABORT` (дефолт) | `INSERT OR ABORT` | Кидает `SQLiteConstraintException`, ничего не вставляет |
| `IGNORE` | `INSERT OR IGNORE` | Тихо пропускает строку, если конфликт |
| `REPLACE` | `INSERT OR REPLACE` | Удаляет старую запись, вставляет новую (опасно — каскадно удалит зависимые строки) |

В `insertCrossRef` мы выбрали `IGNORE`, потому что добавление одного и того же трека в один и тот же альбом — это no-op, не ошибка. С `ABORT` пришлось бы оборачивать вызов в `try/catch`, что хуже.

`insertCrossRefs(List)` (без `IGNORE`) используется в `reorderTracks`, где мы сами гарантируем уникальность (только что удалили все cross-ref альбома).

И финальный метод — транзакционная переустановка порядка треков:

```kotlin
@Dao
interface UserAlbumsDao {

    // ... все выше ...

    /**
     * Транзакция "переупорядочить треки":
     * стираем все связи альбома и создаём заново в новом порядке.
     */
    @Transaction
    suspend fun reorderTracks(albumId: Long, trackIds: List<String>) {
        removeAllCrossRefs(albumId)
        insertCrossRefs(
            trackIds.mapIndexed { index, trackId ->
                UserAlbumTrackCrossRef(albumId, trackId, index)
            }
        )
    }
}
```

`@Transaction suspend fun` с телом — это не запрос, а **транзакционный блок**. Обе операции (`removeAllCrossRefs` + `insertCrossRefs`) выполняются в одной транзакции. Если между ними приложение упадёт — БД откатится в исходное состояние, порядок не потеряется.

Под капотом Room оборачивает тело в `db.withTransaction { ... }` (для suspend) или `db.runInTransaction { ... }` (для не-suspend). На уровне SQLite это `BEGIN TRANSACTION ... COMMIT`/`ROLLBACK`.

`mapIndexed { index, trackId -> ... }` — стандартный stdlib-оператор, как `map`, но лямбда получает два параметра: `index` (с 0) и сам элемент. Используется, когда нужна нумерация в трансформации. Эквивалент через `withIndex`:

```kotlin
trackIds.withIndex().map { (index, trackId) ->
    UserAlbumTrackCrossRef(albumId, trackId, index)
}
```

`mapIndexed` короче и не создаёт промежуточный `IndexedValue`-список.

### Шаг 7 — `AppDatabase`

`AppDatabase` — точка сборки: перечисляем все entity, объявляем DAO-аксессоры, конфигурируем `Room.databaseBuilder`.

Создаём файл — пакет, импорты, аннотация `@Database`, abstract-класс с DAO-методом и фабрика:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/database/AppDatabase.kt
package org.example.mp3player.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.example.mp3player.data.database.dao.UserAlbumsDao
import org.example.mp3player.data.database.entities.UserAlbumEntity
import org.example.mp3player.data.database.entities.UserAlbumTrackCrossRef

@Database(
    entities = [UserAlbumEntity::class, UserAlbumTrackCrossRef::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userAlbumsDao(): UserAlbumsDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context = context.applicationContext,
                klass = AppDatabase::class.java,
                name = "mp3player.db",
            )
                // На этапе разработки — удобно.
                // В проде: настоящие миграции (см. Шаг 9).
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
```

`@Database(entities = [...], version = 1, exportSchema = true)` — список всех entity, версия схемы (нужна для миграций), и флаг `exportSchema` — Room запишет JSON-описание схемы в `schemas/` (полезно для тестов миграций и code review).

`abstract class AppDatabase : RoomDatabase()` — Room сгенерирует `AppDatabase_Impl`, который наследуется от твоего класса и реализует абстрактные методы (`userAlbumsDao()`).

`context.applicationContext` — критично. Если передать `Activity` в `Room.databaseBuilder(context, ...)` — Room удержит ссылку, и после поворота экрана `Activity` не сможет быть собрана GC. Утечка. Всегда `applicationContext`.

**`fallbackToDestructiveMigration(dropAllTables = true)`** — что физически делает. При первом открытии БД Room сравнивает `version` в `@Database(version = N)` с версией, записанной в файле БД. Если они не совпадают и нет подходящей `Migration`:

- **Без `fallback`** → `IllegalStateException: Room cannot verify the data integrity`. Приложение падает, пользователь не может его открыть.
- **С `fallback`** → Room выполняет SQL `DROP TABLE` для всех Room-таблиц и пересоздаёт их по текущей схеме. Все данные **уничтожаются**.

`dropAllTables = true` (флаг с Room 2.6) — явное согласие на «снос всего». Без этого флага Room мог пропустить `room_master_table` или служебные таблицы и оставить мусор.

Когда это допустимо: ранние стадии разработки, beta-тесты — когда данных у пользователей минимум и потеря не критична. **В проде — никогда**: пользователь поставил обновление и потерял свои плейлисты, баг-репорт неминуем. В проде пишутся явные `Migration`-объекты (см. Шаг 9) и тестируются через `MigrationTestHelper` из `androidx.room:room-testing`.

### Шаг 8 — Репозиторий

Реализация `UserAlbumsRepository` на стороне `data`: получает DAO через конструктор, мапит Room-entity → доменные модели, плюс несколько хитрых моментов (read-modify-write для `rename`/`setCover`, `firstValue` для одноразового чтения из `Flow`).

Создаём файл — пакет, импорты, объявление класса с конструктором:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/repository/UserAlbumsRepositoryImpl.kt
package org.example.mp3player.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.example.mp3player.data.database.dao.UserAlbumsDao
import org.example.mp3player.data.database.entities.UserAlbumEntity
import org.example.mp3player.data.database.entities.UserAlbumTrackCrossRef
import org.example.mp3player.data.database.entities.UserAlbumWithTrackIds
import org.example.mp3player.domain.model.UserAlbum
import org.example.mp3player.domain.repository.UserAlbumsRepository

class UserAlbumsRepositoryImpl(
    private val dao: UserAlbumsDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : UserAlbumsRepository {

    // дальше — observe-методы (читают и мапят), CRUD по альбомам, операции с cross-ref, приватные мапперы и firstValue extension
}
```

`dao` приходит через конструктор — стандартный DI. `clock: () -> Long` с дефолтом `System.currentTimeMillis()` — это **инъекция времени**: в тестах можно подменить на фейковый clock и проверить, что `createdAt` записывается правильно. В проде дефолт — реальные часы.

Начнём с reactive observe-методов. Они только читают и мапят Room-entity в доменные модели:

```kotlin
class UserAlbumsRepositoryImpl(
    private val dao: UserAlbumsDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : UserAlbumsRepository {

    override fun observeAll(): Flow<List<UserAlbum>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<UserAlbum?> =
        dao.observeById(id).map { it?.toDomain() }

    // дальше — create/rename/setCover/delete (CRUD по альбомам), операции с cross-ref, мапперы
}
```

Тут две `.map` подряд — но разные. Внешняя — это `Flow.map` (оператор потока), внутренняя — `List.map` (синхронная трансформация коллекции). См. подробнее в `02-PERMISSIONS_AND_SCAN.md`, Шаг 8.

`toDomain()` — приватная extension, которую мы определим в конце. Она превращает `UserAlbumWithTrackIds` (Room) в `UserAlbum` (domain).

Дальше — CRUD по альбомам. `create` простой, а `rename` и `setCover` чуть хитрее: они **сначала читают** текущее значение, потом обновляют:

```kotlin
class UserAlbumsRepositoryImpl(...) : UserAlbumsRepository {

    // observeAll, observeById — выше

    override suspend fun create(title: String, description: String, coverUri: String?): Long =
        dao.insertAlbum(
            UserAlbumEntity(
                title = title,
                description = description,
                coverUri = coverUri,
                createdAt = clock(),
            )
        )

    override suspend fun rename(id: Long, newTitle: String) {
        val current = dao.observeById(id).firstValue() ?: return
        dao.updateAlbum(current.album.copy(title = newTitle))
    }

    override suspend fun setCover(id: Long, coverUri: String?) {
        val current = dao.observeById(id).firstValue() ?: return
        dao.updateAlbum(current.album.copy(coverUri = coverUri))
    }

    override suspend fun delete(id: Long) {
        dao.deleteAlbum(id)
    }

    // дальше — addTrack/removeTrack/reorderTracks (операции с cross-ref) и приватные мапперы
}
```

`create` — `dao.insertAlbum` возвращает `Long` — id новой записи (из `@PrimaryKey(autoGenerate)`). Мы прокидываем его наружу через возврат функции.

`rename` и `setCover` — паттерн **read-modify-write**: достали текущую запись, изменили одно поле через `copy(...)`, записали обратно. `dao.observeById(id).firstValue()` — это «возьми текущее значение из Flow и отпиши́сь». Подробнее про `firstValue` ниже.

`?: return` — если альбома с таким id нет (`firstValue` вернул `null`), просто ничего не делаем (а не падаем). Это безопаснее в реактивном UI: пользователь мог удалить альбом между моментом, когда он нажал «переименовать», и моментом, когда корутина дошла до `rename`.

`current.album.copy(title = newTitle)` — `data class` даёт `copy(...)` бесплатно: новый экземпляр со всеми полями старого, но с подменой одного.

Операции с cross-ref — добавление/удаление треков и переустановка порядка:

```kotlin
class UserAlbumsRepositoryImpl(...) : UserAlbumsRepository {

    // observeAll/observeById — выше
    // create/rename/setCover/delete — выше

    override suspend fun addTrack(albumId: Long, trackId: String) {
        val nextPosition = dao.maxPosition(albumId) + 1
        dao.insertCrossRef(UserAlbumTrackCrossRef(albumId, trackId, nextPosition))
    }

    override suspend fun removeTrack(albumId: Long, trackId: String) {
        dao.removeCrossRef(albumId, trackId)
    }

    override suspend fun reorderTracks(albumId: Long, trackIds: List<String>) {
        dao.reorderTracks(albumId, trackIds)
    }

    // дальше — приватные мапперы toDomain и firstValue extension
}
```

`addTrack` использует `dao.maxPosition(albumId) + 1` — кладём новый трек в конец альбома. Если в альбоме ещё нет треков, `maxPosition` вернёт `-1` (см. `COALESCE` в Шаге 6), и первый трек получит позицию `0`.

`reorderTracks` — просто делегирует DAO-методу с `@Transaction`. Логика «стереть все cross-ref и пересоздать» там же.

И финал — приватные мапперы и extension `firstValue`:

```kotlin
class UserAlbumsRepositoryImpl(...) : UserAlbumsRepository {

    // все override-методы — выше

    private fun UserAlbumWithTrackIds.toDomain(): UserAlbum = UserAlbum(
        id = album.id,
        title = album.title,
        description = album.description,
        coverUri = album.coverUri,
        createdAt = album.createdAt,
        trackIds = orderedTrackIds,
    )
}

/** Синхронно взять первое значение Flow. Использовать только в suspend-контексте. */
private suspend fun <T> Flow<T>.firstValue(): T = first()
```

`toDomain()` — приватная extension на `UserAlbumWithTrackIds`. Конвертирует поля Room-entity в доменную модель. `orderedTrackIds` — computed property из Шага 5, который сортирует cross-ref по `position` и возвращает только trackId.

**`firstValue()` через `Flow.first`.** `Flow.first()` — оператор-терминатор: подписывается на flow, **ждёт первого emit**, отписывается, возвращает значение. Это suspend-функция — поэтому extension сама `suspend`.

Сравни с `Flow.single()`: тоже ждёт первого emit, но **дополнительно** проверяет, что больше эмитов не будет — иначе бросит `IllegalStateException`. Для `StateFlow` (где значения текут потоком) `single()` всегда упадёт. Поэтому нужен именно `first()`.

Зачем `firstValue` в репозитории: иногда надо синхронно получить «текущее состояние» из реактивного источника. Например, в `rename()` — прочитать текущий `UserAlbum`, скопировать с новым title, записать обратно. Это разовый запрос, не подписка.

Альтернатива — добавить в DAO суспенд-метод `suspend fun getById(id: Long): UserAlbumWithTrackIds?` без `Flow`. Это даже корректнее, но мы пошли по пути «один источник правды — `Flow`», и `firstValue()` это закрывает.

### Шаг 9 — Миграция (пример на будущее)

Когда будешь добавлять поле, например `color: Long?` в `user_albums`:

1. Меняем entity — добавляем поле.
2. Увеличиваем `version` в `@Database` до 2.
3. Убираем `fallbackToDestructiveMigration` и добавляем миграцию:

```kotlin
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE user_albums ADD COLUMN color INTEGER")
    }
}

Room.databaseBuilder(context, AppDatabase::class.java, "mp3player.db")
    .addMigrations(MIGRATION_1_2)
    .build()
```

На этапе разработки миграции можно не писать: `fallbackToDestructiveMigration(dropAllTables = true)` при несовпадении версий просто пересоздаст БД. Пользователь потеряет свои плейлисты — для beta нормально. Перед релизом уберёшь.

---

## Подводные камни

### 1. Забытый KSP
Без `add("kspAndroid", libs.androidx.room.compiler)` Room не сгенерирует реализации DAO. Ошибка: `Cannot find implementation for ...AppDatabase. ...AppDatabase_Impl does not exist`.

### 2. Неправильный таргет KSP
Если написать просто `ksp(...)` вместо `add("kspAndroid", ...)` в KMP-модуле — Gradle не поймёт, для какого таргета генерировать. `add("ksp<TargetName>", ...)` — правильно.

### 3. `@Transaction` забыт на `@Relation`-запросах
Room выдаст warning при сборке, но не ошибку. Данные могут прийти несогласованные.

### 4. Миграция без версии
Изменил schema, забыл поднять `version` → при запуске приложения `IllegalStateException: Room cannot verify the data integrity`. `fallbackToDestructiveMigration` спасает только при **правильном** повышении версии.

### 5. `mainActivity` вместо `applicationContext` в DB.build
Если передать `Activity` в `Room.databaseBuilder(context, ...)` — утечка `Activity` после поворота экрана. Всегда `context.applicationContext`.

### 6. Слишком частые обновления
Если писать в таблицу 1000 раз подряд — каждый раз эмитится новое значение `Flow`. Лучше батчить через `insertCrossRefs(List)` или обернуть в `@Transaction`.

### 7. `Long` в `trackId`
В `AudioTrack.id` — `String`. В Room мы тоже храним `trackId: String`. Если где-то случайно используешь `Long.toString()` — получится «123», а из MediaStore в будущем может прийти и «123/something». Всегда `String` end-to-end.

---

## Try yourself

1. **Запусти приложение**, создай альбом `dao.insertAlbum(UserAlbumEntity(title = "Test", createdAt = System.currentTimeMillis()))`, в Database Inspector (Android Studio → View → Tool Windows → App Inspection → Database Inspector) посмотри таблицу `user_albums`.

2. **Добавь поле `isFavorite: Boolean`** в `UserAlbumEntity`. Увеличь version до 2. Напиши миграцию, которая добавит колонку с `DEFAULT 0`. Пропусти через `.addMigrations(MIGRATION_1_2)`.

3. **Добавь DAO-метод**:
   ```kotlin
   @Query("SELECT * FROM user_albums WHERE title LIKE '%' || :query || '%'")
   fun search(query: String): Flow<List<UserAlbumWithTrackIds>>
   ```
   И в репозитории `fun search(query: String): Flow<List<UserAlbum>>`.

4. **Сделай ограничение** — в одном альбоме не больше 500 треков. В `addTrack` проверь `maxPosition + 1 <= 500`, иначе кинь `IllegalStateException`.

5. **Напиши тест (опционально)**: `RoomDatabase.Builder#inMemoryDatabaseBuilder` → создай in-memory БД, вставь альбом, проверь `observeAll().first().size == 1`.

---

## Дальше

→ [`04-PLAYBACK_MEDIA3.md`](./04-PLAYBACK_MEDIA3.md)

## Ссылки

- [Room — Android Developers](https://developer.android.com/training/data-storage/room)
- [Define relationships between objects — @Relation, @Junction](https://developer.android.com/training/data-storage/room/relationships)
- [Kotlin Multiplatform Room (experimental)](https://developer.android.com/kotlin/multiplatform/room)
- [KSP — Getting started](https://kotlinlang.org/docs/ksp-quickstart.html)
