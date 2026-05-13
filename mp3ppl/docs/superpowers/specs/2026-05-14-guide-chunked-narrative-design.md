# Гайд: переход на формат «нарезка-нарратив + приложение для копи-паста»

**Дата:** 2026-05-14
**Область:** `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md` (один файл как образец)
**Статус:** утверждён, готов к плану реализации

---

## Проблема

Сейчас все файлы гайда (`mp3ppl/docs/guide/01..08`) построены по шаблону:

```
### Шаг N — Имя
[весь .kt-файл одной простынёй]
#### Разбор по строкам
##### `выражение_1` → объяснение
##### `выражение_2` → объяснение
...
```

Студент сначала видит большой блок кода без контекста, потом отдельной секцией — построчный разбор. Чтобы связать «что объясняют» с «где это в коде», приходится скроллить туда-сюда. Когда садишься писать тот же код у себя — теряешься, в каком порядке вписывать и куда.

## Цель

Переписать `02-PERMISSIONS_AND_SCAN.md` в формат «build-along»: код идёт маленькими чанками вперемешку с объяснениями, а в самом конце файла — раздел «Готовые файлы для копи-паста» как страховка.

**Критерий успеха (дословно от пользователя):**

> «учебная часть была такой чтобы я разбираясь мог написать то что показано в гайде а если я что-то делаю не так или пропускаю главу — мог просто скопировать из конца».

То есть две дорожки:
- **Учебная (сверху):** идёшь линейно, набираешь код вместе с гайдом, понимаешь каждый кусок до того, как видишь следующий.
- **Шпаргалка (снизу):** если запутался или прыгнул через шаг — копируешь весь файл целиком из приложения.

## Не входит в скоуп

- Изменение архитектурных решений, имён классов, порядка шагов.
- Переписывание содержательной части объяснений (только реорганизация — ничего ценного не выкидывается).
- Остальные 8 файлов гайда (`00-ROADMAP.md`, `01`, `03..08`). Этот файл — образец; решение по остальным примем после.
- Изменение стиля: русский, на «ты», технические термины латиницей — сохраняется.
- Обновление `PROMPT_IMPROVE_GUIDE.md` — отдельная задача, не сейчас.

---

## Целевая структура шага

Каждый `### Шаг N — …` перестраивается из «код-простыня + разбор» в:

1. **Краткое вступление (1–3 предложения).** Что строим в шаге, зачем, какая роль в общей картине.
2. **Чередование «чанк кода → объяснение → связка → следующий чанк».** Гранулярность — по сложности:
   - Тривиальное (`package`, `imports`, объявление пустого класса) — крупно, одним блоком, без подробного разбора.
   - Цепочки операторов и нетривиальные выражения (`groupBy { … }.map { … }.sortedBy { … }`) — мельче, по одному вызову с разбором.
3. **Маркер-путь в каждом чанке.** Первая строка кода-блока — комментарий с полным путём, например:
   ```kotlin
   // shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt
   ```
   Повторяется в каждом чанке этого шага. Это решает «куда вставлять, если открыл файл с середины».
4. **Связки-нарратив между чанками.** Короткие фразы вроде «теперь подключим scanner», «дальше — самое интересное, группировка», «осталось отсортировать». Не делать длинных мостов — одно предложение на переход.
5. **Раздел `#### Разбор по строкам` исчезает как структура.** Его контент перераспределяется по чанкам:
   - Каждое `##### `выражение`` становится объяснением сразу под соответствующим код-чанком.
   - Таблицы (например, `Flow.map` vs `List.map`), примеры (Pink Floyd-альбом для `groupBy`), замечания (про `lowercase()` и Collator) — едут к своему чанку, не теряются.

### Пример «до / после» (Шаг 8 как иллюстрация)

**До (сейчас):**

````
### Шаг 8 — `AlbumsRepositoryImpl` (группировка)

```kotlin
// shared/.../AlbumsRepositoryImpl.kt
package ...
imports ...

class AlbumsRepositoryImpl(
    private val tracksRepository: TracksRepository,
) : AlbumsRepository {

    override fun observeAlbums(): Flow<List<Album>> =
        tracksRepository.observeTracks().map { tracks -> groupIntoAlbums(tracks) }

    override fun observeTracksOfAlbum(albumId: String): Flow<List<Track>> = ...

    private fun groupIntoAlbums(tracks: List<Track>): List<Album> =
        tracks
            .groupBy { it.albumId }
            .map { (albumId, items) -> Album(...) }
            .sortedBy { it.title.lowercase() }
}
```

#### Разбор по строкам

##### `tracksRepository.observeTracks().map { ... }` → ...
##### `tracks.groupBy { it.albumId }` → ...
##### `.map { (albumId, items) -> Album(...) }` → ...
##### `.sortedBy { it.title.lowercase() }` → ...
````

**После:**

````
### Шаг 8 — `AlbumsRepositoryImpl` (группировка)

Этот класс — главный пример «делать почти ничего, но получать реактивность бесплатно». Альбомы нигде не хранятся в поле; они выводятся из текущего списка треков на лету.

Создаём файл и объявляем класс:

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
}
```

`tracksRepository` — единственная зависимость: всё, что нам нужно, мы выведем из его потока треков.

Теперь — поток альбомов:

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt

override fun observeAlbums(): Flow<List<Album>> =
    tracksRepository.observeTracks().map { tracks -> groupIntoAlbums(tracks) }
```

Здесь `.map` — это `Flow.map` из `kotlinx.coroutines.flow`. Каждый раз, когда `TracksRepositoryImpl` обновляет `_tracks.value`, прилетает свежий список — мы прогоняем его через `groupIntoAlbums` и эмитим результат подписчику.

Важно не путать с `List.map`, который встретится через два чанка:

| | `Flow.map` (тут) | `List.map` (дальше) |
| --- | --- | --- |
| Receiver | `Flow<T>` | `List<T>` |
| Когда выполняется | На каждый emit upstream | Один раз, синхронно |
| Импорт | `kotlinx.coroutines.flow.map` | не нужен |

Дальше — `observeTracksOfAlbum` (треки конкретного альбома):

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt

override fun observeTracksOfAlbum(albumId: String): Flow<List<Track>> =
    tracksRepository.observeTracks().map { tracks ->
        tracks.filter { it.albumId == albumId }
            .sortedBy { it.title }
    }
```

Та же `Flow.map` снаружи, внутри — `List.filter` + `List.sortedBy` (синхронно). Сначала отфильтровали треки этого альбома, потом отсортировали по названию.

Теперь — самое интересное, сама группировка:

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt

private fun groupIntoAlbums(tracks: List<Track>): List<Album> =
    tracks
        .groupBy { it.albumId }
```

`groupBy` возвращает `Map<String, List<Track>>`. Ключ — `albumId`, значение — список всех треков с этим `albumId`.

Пример. Допустим, на устройстве 5 треков:
[пример с Pink Floyd как сейчас]

Гарантия: `groupBy` не создаёт пустых групп — это пригодится дальше для безопасного `items.first()`.

Превращаем каждую группу в `Album`:

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt

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
```

`(albumId, items)` — деструктуризация `Map.Entry`: ... [как сейчас]
`items.map { it.artist }.distinct()` — это уже **`List.map`**, синхронный ... [как сейчас]
`if (artists.size == 1) ... else "Various Artists"` — ... [как сейчас]
`title = items.first().album` — безопасно, потому что `groupBy` гарантирует непустые списки ... [как сейчас]
`coverUri = items.firstOrNull { it.coverUri != null }?.coverUri` — хитрая деталь ... [как сейчас]
`totalDurationMs = items.sumOf { it.duration }` — ... [как сейчас]

И финальный шаг — сортировка:

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt

        .sortedBy { it.title.lowercase() }
```

Сортировка `Album`-ов по названию, с приведением к нижнему регистру. Без `.lowercase()`:
[таблица кодпойнтов как сейчас + замечание про Collator]
````

Идея: к моменту, когда читатель видит последний чанк (`.sortedBy`), он уже сам набрал у себя в IDE всё, что было выше, и просто дописывает последнюю строку. Никакого «прокручивать вверх и искать, куда это вставить».

---

## Раздел в самом конце файла: «Готовые файлы (для копи-паста)»

После всех `### Шаг N` добавляется один общий раздел:

```markdown
## Готовые файлы (для копи-паста)

> Если запутался по дороге — здесь полные версии всех файлов, созданных или изменённых в этом этапе. Копируй целиком.

### `shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/Track.kt`
```kotlin
[full code]
```

### `shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/Album.kt`
```kotlin
[full code]
```

### `shared/data/src/commonMain/kotlin/org/example/mp3player/data/MusicScanner.kt` (commonMain `expect`)
```kotlin
[full code]
```

### `shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt`
```kotlin
[full code]
```

[... и так далее по всем файлам этапа ...]
```

**Правила для приложения:**
- Заголовок каждого подраздела — путь файла в backticks.
- Только код, без объяснений (объяснения — в учебной части сверху).
- Порядок — соответствующий порядку появления в шагах (`Track` → `Album` → `MusicScanner` (expect/actual) → `TracksRepositoryImpl` → `AlbumsRepositoryImpl` → манифест → `AudioPermission` и т.д.).
- Если файл создаётся пустым в одном шаге и дополняется в следующих — приложение содержит финальную версию.

---

## Что не меняется

- Архитектура, имена классов, порядок шагов, словарь — всё прежнее.
- Стиль (русский, «ты», термины латиницей).
- Содержательная глубина объяснений (не урезаем — только реорганизуем).
- Остальные файлы `mp3ppl/docs/guide/`. Решение по ним примем, посмотрев на готовый образец.

## Риски и митигация

| Риск | Митигация |
|---|---|
| Файл вырастет в 1.5–1.8 раза из-за дублирования (учебная часть + приложение). | Это сознательная цена за две дорожки. Приложение — без объяснений, минимум воды. |
| При переразложении `#### Разбор по строкам` могут выпасть мелкие нюансы (таблицы, примеры). | Перед коммитом — diff-проверка: каждое `##### `выражение`` из старого разбора должно быть найдено в новом тексте (поиском по ключевым словам). |
| Маркер-путь в каждом чанке — визуальный шум. | Это сознательная цена за «никогда не теряться». Маркер всегда первой строкой, в виде `// path/...` — глаз быстро привыкает игнорировать. |
| Чанки-связки могут стать слишком многословными («теперь, после того как мы…»). | Правило: одна короткая фраза-мост между чанками, максимум одно предложение. |

## Критерий приёмки

- Все шаги (`### Шаг 1` … `### Шаг N`) в `02-PERMISSIONS_AND_SCAN.md` переписаны в формат «нарезка-нарратив».
- Раздел `#### Разбор по строкам` нигде не остался как структура.
- Каждый чанк начинается с маркера-пути.
- В конце файла есть раздел `## Готовые файлы (для копи-паста)` со всеми финальными версиями файлов этого этапа.
- Никакая содержательная информация из старого `#### Разбор по строкам` не потеряна (проверка ключевыми словами по diff).
- Студент, читая сверху вниз, может набрать код у себя без скролла назад.
