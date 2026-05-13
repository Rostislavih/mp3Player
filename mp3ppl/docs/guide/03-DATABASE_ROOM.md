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
├── UserAlbum.kt                            (новый, доменная модель)
└── UserAlbumsRepository.kt                 (новый, интерфейс)

shared/data/src/androidMain/kotlin/org/example/mp3player/data/db/
├── AppDatabase.kt                          (новый, Room DB)
├── entities/
│   ├── UserAlbumEntity.kt                  (новый)
│   ├── UserAlbumTrackCrossRef.kt           (новый)
│   └── UserAlbumWithTrackIds.kt           (новый)
├── dao/
│   └── UserAlbumsDao.kt                    (новый)
└── UserAlbumsRepositoryImpl.kt             (новый)
```

Почему всё под `androidMain`? Room на Android работает отлично. **Room 2.7+** поддерживает KMP, но с ограничениями; для учебного проекта проще положить всё под `androidMain` и опубликовать через `expect/actual` только **интерфейс**, если на iOS будет другая БД (например, SQLDelight).

---

## Реализация

### Шаг 1 — Добавить зависимости

`gradle/libs.versions.toml` — убедись, что есть (или добавь):

```toml
[versions]
room = "2.8.4"
ksp = "2.3.20-2.0.4"

[libraries]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`shared/data/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
}

kotlin {
    // ...
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
}
```

Строка `add("kspAndroid", ...)` — критична. Именно она говорит KSP генерировать код для Android-таргета. Если забыть — получишь `Unresolved reference: UserAlbumsDao_Impl` при сборке.

### Шаг 2 — Доменная модель

```kotlin
// shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/UserAlbum.kt
package org.example.mp3player.domain

data class UserAlbum(
    val id: Long,
    val title: String,
    val description: String,
    val coverUri: String?,
    val createdAt: Long,       // epoch millis
    val trackIds: List<String>,
)
```

**Почему `trackIds: List<String>`, а не `tracks: List<Track>`?** Потому что `UserAlbum` в `domain` не должен знать о том, где лежат треки. Список id — это просто ссылки. Presentation-слой потом сам "подтянет" треки по id через `TracksRepository`. Это развязывает слои.

### Шаг 3 — Интерфейс репозитория

```kotlin
// shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/UserAlbumsRepository.kt
package org.example.mp3player.domain

import kotlinx.coroutines.flow.Flow

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

### Шаг 4 — Room Entity

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/db/entities/UserAlbumEntity.kt
package org.example.mp3player.data.db.entities

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

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/db/entities/UserAlbumTrackCrossRef.kt
package org.example.mp3player.data.db.entities

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

**Почему составной первичный ключ `(albumId, trackId)`?** Один и тот же трек не может быть в одном альбоме дважды. БД сама это гарантирует.

**Почему индексы?** Без них запросы `WHERE albumId = ?` будут полным сканом таблицы. С индексом — мгновенно.

### Шаг 5 — Связка для загрузки альбома со списком trackIds

Room умеет загружать связанные данные одним запросом через `@Relation`. Но `@Relation` нужен промежуточный класс, который опишет связь:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/db/entities/UserAlbumWithTrackIds.kt
package org.example.mp3player.data.db.entities

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

> **Нюанс:** `@Relation` в данном случае грузит `UserAlbumTrackCrossRef` напрямую, а не сами треки. Потому что треки у нас в MediaStore, а не в Room. Мы получаем список id и позиций; сами треки подтянет слой выше.

### Шаг 6 — DAO

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/db/dao/UserAlbumsDao.kt
package org.example.mp3player.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.example.mp3player.data.db.entities.UserAlbumEntity
import org.example.mp3player.data.db.entities.UserAlbumTrackCrossRef
import org.example.mp3player.data.db.entities.UserAlbumWithTrackIds

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: UserAlbumTrackCrossRef)

    @Query("DELETE FROM user_album_track_cross_ref WHERE albumId = :albumId AND trackId = :trackId")
    suspend fun removeCrossRef(albumId: Long, trackId: String)

    @Query("DELETE FROM user_album_track_cross_ref WHERE albumId = :albumId")
    suspend fun removeAllCrossRefs(albumId: Long)

    @Insert
    suspend fun insertCrossRefs(refs: List<UserAlbumTrackCrossRef>)

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

### Шаг 7 — `AppDatabase`

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/db/AppDatabase.kt
package org.example.mp3player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.example.mp3player.data.db.dao.UserAlbumsDao
import org.example.mp3player.data.db.entities.UserAlbumEntity
import org.example.mp3player.data.db.entities.UserAlbumTrackCrossRef

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
                // В проде: настоящие миграции (см. ниже).
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
```

### Шаг 8 — Репозиторий

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/UserAlbumsRepositoryImpl.kt
package org.example.mp3player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.example.mp3player.data.db.dao.UserAlbumsDao
import org.example.mp3player.data.db.entities.UserAlbumEntity
import org.example.mp3player.data.db.entities.UserAlbumTrackCrossRef
import org.example.mp3player.data.db.entities.UserAlbumWithTrackIds
import org.example.mp3player.domain.UserAlbum
import org.example.mp3player.domain.UserAlbumsRepository

class UserAlbumsRepositoryImpl(
    private val dao: UserAlbumsDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : UserAlbumsRepository {

    override fun observeAll(): Flow<List<UserAlbum>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<UserAlbum?> =
        dao.observeById(id).map { it?.toDomain() }

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
private suspend fun <T> Flow<T>.firstValue(): T =
    kotlinx.coroutines.flow.first(this)
```

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

## Разбор

### Кто читает аннотации Room и когда

Аннотации `@Entity`, `@PrimaryKey`, `@Dao`, `@Query`, `@Insert`, `@Transaction` — это **не магия Kotlin**. Сам Kotlin их видит как метки, без поведения. Их читает **KSP-процессор Room** в момент сборки проекта.

Что физически происходит при `./gradlew :shared:data:assembleDebug`:

1. Kotlin-компилятор парсит твои файлы.
2. KSP запускает зарегистрированные процессоры. У Room процессор зарегистрирован через `add("kspAndroid", libs.androidx.room.compiler)` в `build.gradle.kts`.
3. Процессор Room обходит классы, помеченные `@Database`, `@Dao`, `@Entity`. Для каждого `@Dao` он **генерирует Java/Kotlin-файл с реализацией**: например, для `UserAlbumsDao` будет создан `UserAlbumsDao_Impl`.
4. Сгенерированные файлы лежат в `build/generated/ksp/<sourceSet>/kotlin/` — IDE их подхватывает, можно открыть и посмотреть глазами (полезно для отладки «что Room делает с моим запросом»).
5. Дальше эти файлы тоже компилируются — и попадают в финальный APK.

То есть `@Dao interface UserAlbumsDao` — это **«заявка»**, а реальный класс с реализацией — `UserAlbumsDao_Impl`. Когда ты вызываешь `db.userAlbumsDao()`, возвращается экземпляр `_Impl`-класса.

#### Почему именно `add("kspAndroid", ...)`, а не просто `ksp(...)`

`ksp(...)` — это короткий вариант для одно-таргетного проекта (обычная Android-библиотека). В KMP-модуле таргетов несколько (`androidTarget`, `iosX64`, `iosArm64`...), и для каждого Gradle создаёт свою configuration: `kspAndroid`, `kspIosX64`, `kspIosArm64`.

`add("kspAndroid", libs.androidx.room.compiler)` говорит: «прогоняй Room-процессор только в Android-таргете». Это правильно — Room работает только под JVM/Android, и попытка запустить его на iOS-таргете провалится (нет JVM-классов, на которые опирается Room). Если бы написали `ksp(...)` без префикса — Gradle не знал бы, к какому таргету привязать процессор.

Сообщение об ошибке без правильного `add(...)`: `Cannot find implementation for ... AppDatabase. AppDatabase_Impl does not exist`. Это значит, что процессор не запустился и `_Impl`-классов нет.

### `@Entity(tableName = "user_albums")` и `@PrimaryKey(autoGenerate = true) val id: Long = 0`

`@Entity` — Room возьмёт этот data class и создаст из него таблицу. Каждое `val` поле без аннотаций станет колонкой. `tableName` задаёт явное имя; без него используется имя класса (`UserAlbumEntity` → таблица `UserAlbumEntity`, что не очень).

`@PrimaryKey(autoGenerate = true)` + `val id: Long = 0` — это **идиома «Room сам выдаст id при insert»**. Работает так:

- Когда вызываешь `dao.insertAlbum(UserAlbumEntity(title = "...", createdAt = ...))`, ты не передаёшь `id`. Дефолтное `0` означает «новая запись».
- Room сгенерирует SQL `INSERT INTO user_albums (id, title, ...) VALUES (NULL, ?, ...)`. Передача `NULL` в `INTEGER PRIMARY KEY AUTOINCREMENT` — сигнал SQLite сгенерировать следующий свободный id.
- `@Insert`-метод возвращает `Long` — это сгенерированный id, который ты дальше используешь (например, чтобы добавить cross-ref'ы к новому альбому).

Если случайно передать ненулевой id (`UserAlbumEntity(id = 42, ...)`):
- При `OnConflictStrategy.ABORT` (дефолт) и существующем id=42 — `SQLiteConstraintException`.
- При `REPLACE` — старая строка удалится, новая встанет на её место (с тем же id).
- При `IGNORE` — insert тихо ничего не сделает, вернёт `-1`.

### `primaryKeys = ["albumId", "trackId"]` — составной первичный ключ

```kotlin
@Entity(
    tableName = "user_album_track_cross_ref",
    primaryKeys = ["albumId", "trackId"],
    indices = [Index("albumId"), Index("trackId")],
)
data class UserAlbumTrackCrossRef(...)
```

Составной ключ означает: уникальной должна быть **пара** `(albumId, trackId)`, а не каждое поле по отдельности. SQLite сам обеспечивает это: попытка вставить вторую строку с такой же парой → `UNIQUE constraint failed`.

Альтернативный дизайн: добавить отдельный `@PrimaryKey id: Long = 0` (auto-generated) и UNIQUE INDEX на пару. Это работает, но избыточно — лишняя колонка, лишний индекс. Составной PK прямее выражает суть: «связь определяется парой».

### `@Index("albumId")` — что физически

Без индекса запрос `SELECT * FROM user_album_track_cross_ref WHERE albumId = 42` идёт **полным сканом**: SQLite читает каждую строку таблицы и сравнивает поле `albumId` с 42. Сложность O(N).

С индексом SQLite поддерживает отдельную **B-tree-структуру**, в которой ключи (`albumId`) отсортированы. Поиск по B-tree — O(log N). На таблице из 100 000 строк это разница в ~10 000 раз.

Цена: на каждый `INSERT/UPDATE/DELETE` индекс надо обновлять. Поэтому индексы ставят только на колонки, по которым **действительно фильтруют** — у нас это `albumId` (запросы «треки этого альбома») и `trackId` (запросы «в каких альбомах этот трек»).

Не индексируй колонки, которые не используются в `WHERE`/`JOIN`/`ORDER BY` — это пустая трата места и времени.

### `@Embedded` + `@Relation` + `Junction`

```kotlin
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
)
```

Здесь Room делает **два SQL-запроса** и сшивает результаты в одну Kotlin-структуру.

**`@Embedded val album: UserAlbumEntity`** — поля entity «вплавляются» прямо в этот класс. На уровне SQL это просто `SELECT * FROM user_albums` — все колонки `UserAlbumEntity` лягут в `album`.

**`@Relation(...)`** — описание «как найти связанные записи». Два пути:

1. **Прямая связь.** Если бы `UserAlbumTrackCrossRef` имел поле, ссылающееся прямо на родителя (`val parentAlbumId: Long`), хватило бы `@Relation(parentColumn = "id", entityColumn = "parentAlbumId")` — Room сам делает `SELECT * FROM cross_ref WHERE parentAlbumId = ?` для каждого родителя.

2. **Через junction-таблицу (наш случай).** Связь many-to-many не выражается одной колонкой. Junction — отдельная таблица-склейка с двумя FK. `Junction(value = UserAlbumTrackCrossRef::class, parentColumn = "albumId", entityColumn = "trackId")` говорит: «найди записи в `UserAlbumTrackCrossRef` через `albumId`, потом по `trackId` достань цели». В нашем случае «цели» — это `UserAlbumTrackCrossRef` (то есть мы вытаскиваем сами junction-записи, не пытаемся проникнуть в `Track`-таблицу, которой и нет).

В рантайме Room делает примерно так:

```sql
-- Запрос 1: родители
SELECT * FROM user_albums ORDER BY createdAt DESC;

-- Запрос 2: для каждого родителя — junction-записи
SELECT * FROM user_album_track_cross_ref WHERE albumId IN (1, 2, 3, ...);
```

Дальше Room в Kotlin-коде группирует результат по `albumId` и собирает `List<UserAlbumWithTrackIds>`.

### Зачем `@Transaction` именно с `@Relation`

Между запросом 1 и запросом 2 проходит немного времени, но достаточно, чтобы кто-то успел вставить/удалить строки в одной из таблиц. Сценарий:

1. `SELECT * FROM user_albums` — получили альбомы A, B, C.
2. *Параллельно* другой код вставляет связь «трек T в альбом A» и удаляет альбом B.
3. `SELECT * FROM cross_ref WHERE albumId IN (1, 2, 3)` — увидим новую связь для A, увидим (или не увидим) связи B.

В результате на руках Kotlin-структура «альбом B существует, но его связи уже непонятно от чего». Внутренне несогласованно.

`@Transaction` оборачивает **обе SQL-команды** в один транзакционный блок: SQLite держит read-snapshot на всё время. Никакие изменения извне не повлияют — гарантирована «согласованная картина мира на момент начала запроса».

Без `@Transaction` Room выдаст compile-warning, но не ошибку — поэтому легко забыть. Привычка: `@Relation` ⇒ всегда `@Transaction`.

### `Flow<List<X>>` в DAO — invalidation tracker

```kotlin
@Query("SELECT * FROM user_albums ORDER BY createdAt DESC")
fun observeAll(): Flow<List<UserAlbumWithTrackIds>>
```

Когда метод DAO возвращает `Flow<...>` (или `LiveData`, или `PagingSource` — Room поддерживает несколько reactive-форм), генератор делает не «один раз выполнить запрос», а **подписку**:

1. Room держит `InvalidationTracker` — внутренний компонент, который слушает все `INSERT/UPDATE/DELETE` через SQLite-триггеры.
2. На этапе генерации `_Impl` Room анализирует SQL запроса и понимает, на какие таблицы он смотрит. Здесь — `user_albums` плюс (из-за `@Transaction` + `@Relation`) `user_album_track_cross_ref`.
3. `Flow` подписывается на изменения этих таблиц.
4. При любом write в наблюдаемые таблицы tracker помечает запрос «грязным» и эмитит новое значение в `Flow` — фактически перезапускает SQL-запрос и шлёт результат.

То есть **«магия» имеет конкретный механизм**: триггеры в SQLite + tracker в Java + конвертация в Flow. Можно даже посмотреть в `build/generated/ksp/...` сгенерированный код — там всё это явно.

Подвох: tracker инвалидирует **по таблице целиком**, не по конкретным строкам. Если ты пишешь в `user_albums` 1000 раз подряд — Flow эмитнет 1000 раз. Решение — батч в одной `@Transaction`-функции (одна транзакция = один эмит).

### `suspend` функции в DAO — какой диспатчер

Room автоматически выполняет `suspend`-методы DAO **не в Main**. Куда именно — определяется при создании БД:

```kotlin
Room.databaseBuilder(...)
    .setQueryExecutor(...)        // для не-suspend
    .setTransactionExecutor(...)  // для @Transaction
    .build()
```

Дефолт — внутренний пул потоков Room (`ArchTaskExecutor`). Это **не** `Dispatchers.IO`, но логически близко: фоновый пул для I/O-операций.

Для `suspend`-метода Room делает примерно `withContext(roomQueryDispatcher) { ...запрос... }`. Поэтому ты можешь спокойно вызывать `dao.observeAll().first()` или `dao.insertAlbum(...)` из `viewModelScope.launch { ... }` (который по дефолту на Main) — Room сам переключится.

Если бы был не-`suspend` метод, возвращающий `Long`/`List<X>` напрямую (т.е. синхронный SQL-запрос), Room бы выкинул `IllegalStateException: Cannot access database on the main thread`. Это намеренная защита от случайного UI-фриза.

### `OnConflictStrategy.IGNORE` vs `REPLACE` vs `ABORT`

`@Insert(onConflict = ...)` — настройка для `INSERT INTO ...`:

| Стратегия | SQL | Что делает при конфликте PK/UNIQUE |
|---|---|---|
| `ABORT` (дефолт) | `INSERT OR ABORT` | Кидает `SQLiteConstraintException`, ничего не вставляет |
| `IGNORE` | `INSERT OR IGNORE` | Тихо пропускает строку, если конфликт |
| `REPLACE` | `INSERT OR REPLACE` | Удаляет старую запись, вставляет новую (опасно — каскадно удалит зависимые строки) |

В `insertCrossRef` мы выбрали `IGNORE`, потому что добавление одного и того же трека в один и тот же альбом — это no-op, не ошибка. С `ABORT` пришлось бы оборачивать вызов в `try/catch`, что хуже.

### `mapIndexed { index, trackId -> ... }`

```kotlin
trackIds.mapIndexed { index, trackId ->
    UserAlbumTrackCrossRef(albumId, trackId, index)
}
```

`mapIndexed` — стандартный оператор stdlib, как `map`, но лямбда получает два параметра: `index` (с 0) и сам элемент. Используется, когда нужна нумерация в трансформации.

Эквивалент через обычный `map`:

```kotlin
trackIds.withIndex().map { (index, trackId) ->
    UserAlbumTrackCrossRef(albumId, trackId, index)
}
```

`mapIndexed` короче и не создаёт промежуточный `IndexedValue`-список.

### `Flow.first(this)` в `firstValue()`

```kotlin
private suspend fun <T> Flow<T>.firstValue(): T =
    kotlinx.coroutines.flow.first(this)
```

`Flow.first()` — оператор-терминатор: подписывается на flow, **ждёт первого emit**, отписывается, возвращает значение. Это suspend-функция — поэтому extension сама `suspend`.

Сравни с `Flow.single()`: тоже ждёт первого emit, но **дополнительно** проверяет, что больше эмитов не будет — иначе бросит `IllegalStateException`. Для `StateFlow` (где значения текут потоком) `single()` всегда упадёт; нужен именно `first()`.

Зачем нам `firstValue()` в репозитории: иногда надо синхронно получить «текущее состояние» из реактивного источника. Например, в `rename()` — прочитать текущий `UserAlbum`, скопировать с новым title, записать обратно. Это разовый запрос, не подписка.

Альтернатива — использовать DAO-метод `suspend fun getById(id: Long): UserAlbumEntity?` без `Flow`. Это даже корректнее, но мы пошли по пути «один источник правды — `Flow`», и `firstValue()` это закрывает.

### `fallbackToDestructiveMigration(dropAllTables = true)`

```kotlin
Room.databaseBuilder(...)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()
```

Что физически делает: при первом открытии БД Room сравнивает `version` в `@Database(version = N)` с версией, записанной в файле БД. Если они не совпадают и нет подходящей `Migration`:

- **Без `fallback`** → `IllegalStateException: Room cannot verify the data integrity`. Приложение падает, пользователь не может его открыть.
- **С `fallback`** → Room выполняет SQL `DROP TABLE` для всех Room-таблиц и пересоздаёт их по текущей schema. Все данные **уничтожаются**.

`dropAllTables = true` (был флаг с Room 2.6) — явное согласие на «снос всего». Без этого флага Room мог пропустить `room_master_table` или служебные таблицы и оставить мусор.

Когда это допустимо: ранние стадии разработки, beta-тесты, когда данных у пользователей минимум и потеря не критична. В проде — никогда: пользователь поставил обновление и потерял свои плейлисты, баг-репорт неминуем.

В проде делается так: пишешь явные `Migration`-объекты для каждого перехода версий, тестируешь их через `MigrationTestHelper` из `androidx.room:room-testing`. Это выходит за рамки гайда; см. `### Шаг 9 — Миграция`.

### `@Transaction suspend fun reorderTracks(...)`

Метод с `@Transaction` работает как пакет: обе операции (`removeAll` + `insertBatch`) выполняются в одной транзакции. Если между ними приложение упадёт — БД откатится в исходное состояние, порядок не потеряется.

Под капотом Room оборачивает тело метода в `db.runInTransaction { ... }` (для не-suspend) или `db.withTransaction { ... }` (для suspend). На уровне SQLite это `BEGIN TRANSACTION ... COMMIT`/`ROLLBACK`.

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
В `domain.Track.id` — `String`. В Room мы тоже хранили `trackId: String`. Если где-то случайно используешь `Long.toString()` — получится "123", а из MediaStore может прийти "123" или "123/something" в будущем. Всегда `String` end-to-end.

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
