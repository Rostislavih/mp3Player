# Guide chunked-narrative restructure — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Переписать `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md` из формата «весь .kt одним блоком + #### Разбор по строкам отдельно» в формат «нарезка-нарратив»: код маленькими чанками вперемешку с объяснениями, плюс новый раздел `## Готовые файлы (для копи-паста)` в самом конце файла.

**Architecture:** Файл правится in-place. Каждый шаг (`### Шаг N`) переписывается отдельной задачей: текущий код-блок дробится на чанки по логике, материал из соответствующего `#### Разбор по строкам` (если есть) перераспределяется по чанкам, между чанками — связки-нарратив. Цельный код шагов перекочёвывает в новый раздел-приложение в конце файла. Сквозной `## Разбор` (строки 717–851 текущего файла) тоже распадается: каждая его подсекция уезжает в свой шаг. После всех правок — верификация по ключевым фразам, что ничего содержательного не потеряно.

**Tech Stack:** Markdown, никаких сборок и тестов. Verification — через `grep` по ключевым фразам.

**Spec:** `mp3ppl/docs/superpowers/specs/2026-05-14-guide-chunked-narrative-design.md`

---

## Карта изменений

**Один файл:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

**Текущая структура (строки):**
| Раздел | Строки | Что с ним делаем |
|---|---|---|
| `# 02. Разрешения и сканирование музыки` (заголовок + intro) | 1–58 | Не трогаем |
| `## Реализация` → `### Шаг 1` (Track) | 61–91 | Реструктуризация (Task 1) |
| `### Шаг 2` (Album) | 93–107 | Реструктуризация (Task 2) |
| `### Шаг 3` (интерфейсы репозиториев) | 109–139 | Реструктуризация (Task 3) |
| `### Шаг 4` (expect MusicScanner) | 141–156 | Реструктуризация (Task 4) |
| `### Шаг 5` (Android реализация + фикс бага) | 158–252 | Реструктуризация + инлайн `withContext`, `use`, `orFallback` из общего `## Разбор` (Task 5) |
| `### Шаг 6` (iOS-заглушка) | 254–267 | Реструктуризация (Task 6) |
| `### Шаг 7` (TracksRepositoryImpl + Разбор по строкам) | 269–365 | Реструктуризация: материал из `#### Разбор по строкам` редистрибутируется по чанкам (Task 7) |
| `### Шаг 8` (AlbumsRepositoryImpl + Разбор по строкам) | 366–536 | То же (Task 8) |
| `### Шаг 9` (XML манифест) | 538–559 | Реструктуризация (Task 9) |
| `### Шаг 10` (Compose permission + Разбор по строкам) | 561–715 | Реструктуризация + инлайн `Build.VERSION.SDK_INT >=` из общего `## Разбор` (Task 10) |
| `## Разбор` (сквозной) | 717–851 | Удалить целиком — всё содержимое мигрирует в шаги (Task 11) |
| `## Подводные камни` | 853–880 | Не трогаем |
| `## Try yourself` | 884–898 | Не трогаем |
| (новое) `## Готовые файлы (для копи-паста)` | — | Создать (Task 12) |
| `## Дальше` + `## Ссылки` | 901–910 | Не трогаем |

**Финальная порядковая структура файла:**
```
# 02. Разрешения и сканирование музыки
## Зачем
## Что реализуем
## Реализация
  ### Шаг 1 — Перенести Track в domain         ← реструктурирован
  ### Шаг 2 — Добавить Album в domain          ← реструктурирован
  ### Шаг 3 — Интерфейсы репозиториев          ← реструктурирован
  ### Шаг 4 — expect MusicScanner              ← реструктурирован
  ### Шаг 5 — Android-реализация               ← + withContext/use/orFallback инлайн
  ### Шаг 6 — iOS-заглушка                     ← реструктурирован
  ### Шаг 7 — TracksRepositoryImpl             ← Разбор инлайн
  ### Шаг 8 — AlbumsRepositoryImpl             ← Разбор инлайн
  ### Шаг 9 — Permission в манифесте           ← реструктурирован
  ### Шаг 10 — Compose-обёртка                 ← Разбор инлайн + Build.VERSION
## Подводные камни
## Try yourself
## Готовые файлы (для копи-паста)              ← НОВОЕ
## Дальше
## Ссылки
```

---

## Принципы для всех задач

Эти правила едины для каждой реструктуризации. Не повторяю их в каждой задаче — храню тут.

### Структура чанка кода

```
[короткая фраза-мост: «теперь добавим X»]

```kotlin
// полный/путь/к/файлу.kt

[фрагмент кода]
```

[объяснение этого фрагмента]
```

- **Маркер-путь** — первой строкой каждого код-блока, как комментарий.
- **Связки** — одно короткое предложение между чанками. Никаких многоэтажных мостов.
- **Гранулярность** — по сложности. Тривиальные вещи (`package`, `imports`, объявление пустого класса) — крупно. Цепочки операторов (`groupBy { … }.map { … }.sortedBy { … }`) — мельче, по одному вызову.

### Структура шага

```
### Шаг N — Имя

[1–3 предложения вступления: что строим, зачем, какая роль]

[чанк 1 → объяснение]
[связка]
[чанк 2 → объяснение]
[связка]
...
[последний чанк → объяснение]
```

### Что нельзя терять

Когда переразлагаешь старый `#### Разбор по строкам` — каждый `##### `выражение`` подзаголовок должен иметь соответствие в новом тексте. Содержание (таблицы, примеры, замечания) встраивается в объяснение под нужный чанк, не урезается.

После каждой задачи — верификация (см. шаги задач): grep по ключевым фразам/таблицам/именам функций, чтобы убедиться, что ничего не выпало.

### Стиль

- Русский, на «ты».
- Технические термины латиницей (`Flow`, `Mutex`, `StateFlow`, `MediaStore`).
- Без эмодзи.
- Без дописываний «TODO допишу позже».
- Не выдумывать API: если в чанке упоминается метод/класс — он должен реально существовать (проверять в `shared/`).

---

### Task 1: Реструктурировать Шаг 1 (`Track` в `domain`)

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md` строки 61–91

**Текущая структура (что заменяем):**

```
### Шаг 1 — Перенести `Track` в `domain`

[предложение про "перенеси в domain"]

```kotlin
// shared/domain/.../Track.kt
package ...

data class Track(...)
```

[абзац про albumId]

[абзац про build.gradle.kts]

```kotlin
commonMain.dependencies { ... }
```
```

Старый текст уже довольно близок к нарезке — здесь работы немного. Шаг короткий и без `#### Разбор по строкам`.

**Целевая структура:**

```
### Шаг 1 — Перенести `Track` в `domain`

Сейчас `Track` лежит в `shared/data/...`, но это модель — её место в `domain`. Переносим файл и заодно добавляем поле `albumId`, без которого следующие шаги невозможны.

[чанк 1: Track.kt с маркером-путём]

Новое поле — `albumId`. Зачем оно нужно: ... [абзац про два альбома с одним названием — сохранить как есть].

После переноса — пропишем зависимость в `shared/data/build.gradle.kts`:

[чанк 2: dependencies-блок с маркером-путём `// shared/data/build.gradle.kts`]

`shared:data` теперь видит модели из `shared:domain`, и реализации репозиториев в этом этапе будут компилироваться.
```

- [ ] **Step 1: Открыть файл и удалить строки 61–91**

Удалить целиком текущий блок `### Шаг 1` (от строки 61 до строки 91 включительно — то есть всё до пустой строки перед `### Шаг 2`).

- [ ] **Step 2: Вставить на это место новую версию шага**

```markdown
### Шаг 1 — Перенести `Track` в `domain`

Сейчас `Track` лежит в `shared/data/src/commonMain/kotlin/org/example/mp3player/data/Track.kt`, но это модель — её место в `domain`. Переносим файл и заодно добавляем поле `albumId`, без которого следующие шаги невозможны.

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

Новое поле — `albumId`. Без него мы не сможем сгруппировать треки в альбомы корректно: два альбома с одним названием, но разными исполнителями — это разные альбомы, а отличает их именно `albumId` (его MediaStore присваивает на уровне «название + исполнитель»).

После переноса — пропишем зависимость в Gradle, чтобы `shared:data` мог видеть модели:

```kotlin
// shared/data/build.gradle.kts

commonMain.dependencies {
    implementation(project(":shared:domain"))
    implementation(libs.kotlinx.coroutines.core)
}
```

Теперь реализации репозиториев из этапа смогут импортировать `org.example.mp3player.domain.Track`, и проект соберётся.
```

- [ ] **Step 3: Verify — содержательные элементы на месте**

Запустить:
```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "albumId","два альбома с одним названием","implementation\(project\(`":shared:domain`"\)\)"
```

Все три паттерна должны найтись.

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): restructure Шаг 1 (Track) to chunked-narrative format"
```

---

### Task 2: Реструктурировать Шаг 2 (`Album` в `domain`)

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md` (бывшие строки 93–107, после Task 1 номера сдвинутся)

**Текущая структура:** `### Шаг 2 — Добавить Album в domain` → один код-блок, без объяснений отдельно.

**Целевая структура:**

```
### Шаг 2 — Добавить `Album` в `domain`

Альбом — отдельная модель, потому что у него своя пачка свойств, которых нет у трека (`trackCount`, `totalDurationMs`, агрегированный `artist`).

[чанк: Album.kt с маркером-путём]

Главное про эту модель — она НЕ хранится в БД и НЕ собирается вручную. На этапе 8 мы научимся выводить `List<Album>` из `List<Track>` через `groupBy`, поэтому сейчас мы только описываем форму данных.

Заметки по полям:
- `id` — это `albumId` из MediaStore (строкой, чтобы единообразно с `Track.albumId`).
- `artist` — для сборников будет `"Various Artists"` (логику решения соберём в `AlbumsRepositoryImpl`).
- `coverUri: String?` — может быть `null`, если у всех треков альбома обложка не нашлась.
- `totalDurationMs` — миллисекунды; форматирование в «3 ч 12 мин» оставим UI-слою.
```

- [ ] **Step 1: Удалить старый Шаг 2**

Найти текущий `### Шаг 2 — Добавить Album в domain` и удалить от заголовка до пустой строки перед `### Шаг 3`.

- [ ] **Step 2: Вставить новую версию**

```markdown
### Шаг 2 — Добавить `Album` в `domain`

Альбом — отдельная модель, потому что у него своя пачка свойств, которых нет у трека (количество дорожек, суммарная длительность, агрегированный исполнитель).

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

Главное про эту модель — она **не хранится** в БД и не собирается вручную. На этапе 8 мы научимся выводить `List<Album>` из `List<Track>` через `groupBy`, и сейчас просто фиксируем форму данных.

Заметки по полям:
- `id` — это `albumId` из MediaStore (строкой, чтобы единообразно с `Track.albumId`).
- `artist` — для сборников будет `"Various Artists"`, логику решения соберём в `AlbumsRepositoryImpl`.
- `coverUri: String?` — может быть `null`, если у всех треков альбома обложку MediaStore не нашёл.
- `totalDurationMs` — миллисекунды; форматирование в «3 ч 12 мин» оставим UI-слою.
```

- [ ] **Step 3: Verify**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "Various Artists","data class Album","totalDurationMs"
```

Все три паттерна должны найтись.

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): restructure Шаг 2 (Album) to chunked-narrative format"
```

---

### Task 3: Реструктурировать Шаг 3 (интерфейсы репозиториев)

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

**Текущая структура:** Два .kt-файла подряд (`TracksRepository.kt`, `AlbumsRepository.kt`) с минимумом текста между.

**Целевая структура:**

```
### Шаг 3 — Интерфейсы репозиториев

Репозиторий — это абстракция «откуда берём данные». Интерфейс лежит в `domain` (чистый Kotlin, без зависимостей от Android), а реализация — в `data`. Это даёт три преимущества: тестировать ViewModel можно с фейковым репозиторием, реализацию можно поменять (например, добавить кэш) без правок UI, и `domain` остаётся переиспользуемым на iOS.

[чанк 1: TracksRepository.kt]

`Flow<List<Track>>` — это «горячая трубка», по которой каждый раз, когда список треков меняется, прилетает новый снимок. Подробно `Flow` разберём в Шаге 7, где появится первая реализация.

`suspend fun refresh()` — корутинная функция (не обычная). Зачем — сканирование MediaStore блокирующее, и `suspend` — это контракт «зови меня из корутины, я могу заснуть».

[чанк 2: AlbumsRepository.kt]

Тут даже нет `suspend` — альбомы выводятся из треков на лету, никакого собственного «обновления» им не нужно. Когда подписчик `observeAlbums()` подцепляется, он автоматически переэмитит при каждом обновлении треков (это мы соберём в Шаге 8).

`observeTracksOfAlbum(albumId)` — отдельный метод, потому что показывать «треки альбома X» — частая операция и логично иметь её прямо тут, а не делать `observeTracks().map { it.filter { ... } }` в каждом ViewModel.
```

- [ ] **Step 1: Удалить старый Шаг 3**

Найти `### Шаг 3 — Интерфейсы репозиториев` и удалить до пустой строки перед `### Шаг 4`.

- [ ] **Step 2: Вставить новую версию**

```markdown
### Шаг 3 — Интерфейсы репозиториев

Репозиторий — это абстракция «откуда берём данные». Интерфейс лежит в `domain` (чистый Kotlin, без зависимостей от Android), а реализация — в `data`. Это даёт три преимущества: тестировать ViewModel можно с фейковым репозиторием, реализацию можно поменять (добавить кэш, источник, что угодно) без правок UI, и `domain` остаётся переиспользуемым на iOS.

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

`Flow<List<Track>>` — это «горячая трубка», по которой каждый раз, когда список треков меняется, прилетает новый снимок. Подробно `Flow` разберём в Шаге 7, где появится первая реализация.

`suspend fun refresh()` — корутинная функция (не обычная). Зачем — сканирование MediaStore блокирующее, и `suspend` — это контракт «зови меня из корутины, я могу заснуть».

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

Тут даже нет `suspend` — альбомы выводятся из треков на лету, никакого собственного «обновления» им не нужно. Когда подписчик `observeAlbums()` подцепляется, он автоматически переэмитит при каждом обновлении треков (это мы соберём в Шаге 8).

`observeTracksOfAlbum(albumId)` — отдельный метод, потому что показывать «треки альбома X» — частая операция и логично иметь её прямо тут, а не делать `observeTracks().map { it.filter { … } }` в каждом ViewModel.
```

- [ ] **Step 3: Verify**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "interface TracksRepository","interface AlbumsRepository","observeTracksOfAlbum"
```

Все три паттерна должны найтись.

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): restructure Шаг 3 (repository interfaces) to chunked-narrative format"
```

---

### Task 4: Реструктурировать Шаг 4 (`expect class MusicScanner`)

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

**Целевая структура:**

```
### Шаг 4 — `expect` MusicScanner

KMP-механика: один и тот же тип, разные реализации на каждой платформе. В `commonMain` мы пишем `expect`-заголовок (контракт), в `androidMain` и `iosMain` — `actual`-реализации.

[чанк: MusicScanner.kt — expect]

`expect class MusicScanner` — обещание компилятору: «тип с таким именем и такими методами будет, конкретная реализация — в платформенных source set'ах». Если для какой-то платформы `actual class MusicScanner` не написан — модуль для этой платформы не соберётся.

`suspend fun scanTracks()` — обязательно `suspend` уже на уровне `expect`, потому что любая реализация будет блокирующей (диск, IPC, нативный API). `actual` обязан сохранить ту же подпись.
```

- [ ] **Step 1: Удалить старый Шаг 4**

Найти `### Шаг 4 — expect MusicScanner` и удалить до пустой строки перед `### Шаг 5`.

- [ ] **Step 2: Вставить новую версию**

```markdown
### Шаг 4 — `expect` MusicScanner

KMP-механика: один и тот же тип, разные реализации на каждой платформе. В `commonMain` мы пишем `expect`-заголовок (контракт), в `androidMain` и `iosMain` — `actual`-реализации.

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
```

- [ ] **Step 3: Verify**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "expect class MusicScanner","actual class MusicScanner","commonMain"
```

Все три паттерна должны найтись (последний — встречается во многих местах, это OK).

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): restructure Шаг 4 (expect MusicScanner) to chunked-narrative format"
```

---

### Task 5: Реструктурировать Шаг 5 (Android-реализация MusicScanner) + инлайн `withContext`/`use`/`orFallback`

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

**Что переразлагается:**
- Текущий Шаг 5 (строки 158–252): фикс бага + полная реализация `MusicScanner.android.kt`.
- Из общего `## Разбор` в этот шаг мигрируют **три** подсекции:
  - `withContext(Dispatchers.IO)` — что физически происходит (включая таблицу диспатчеров и `withContext` vs `launch`).
  - `cursor?.use { ... }` — try-with-resources в Kotlin.
  - `String?.orFallback(fallback)` — extension на nullable receiver.

Эти три подсекции в Task 11 будут удалены из общего раздела `## Разбор`. Здесь — встраиваем их в чанки.

**Целевая структура:**

```
### Шаг 5 — Android-реализация (фикс бага + `actual`)

Сначала — баг. В существующем коде на строке 23 — лишняя закрывающая фигурная скобка в SQL-выражении:

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

Дальше — полная актуализированная реализация. Разберём по чанкам.

Объявление класса:

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
    // ...
}
```

`Context` нужен, чтобы достучаться до `contentResolver` — это единственный способ задать запрос к `MediaStore`. На iOS его не будет — там `actual` будет без параметров (см. Шаг 6).

Главный метод — `scanTracks`. Снаружи он `suspend`, внутри — переключение на IO-диспатчер:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt

actual suspend fun scanTracks(): List<Track> = withContext(Dispatchers.IO) {
    // ...
}
```

`Dispatchers` — это объекты, которые умеют запускать корутину на нужном пуле потоков. У kotlinx.coroutines их три ходовых:

| Диспетчер | Где живёт | Для чего |
|---|---|---|
| `Dispatchers.Main` | Один Android UI-thread | Всё, что трогает Compose/View — рисование, чтение состояния, обновление UI |
| `Dispatchers.Default` | Пул на `Runtime.availableProcessors()` потоков | CPU-интенсивная работа (парсинг, обработка изображений, сортировка миллиона элементов) |
| `Dispatchers.IO` | Пул до 64 потоков (можно конфигурировать), потоки могут «висеть в ожидании» | Блокирующие I/O — файлы, сеть, БД, `ContentResolver` |

Различие `Default` vs `IO` — про допустимое блокирование. Поток в `Default`-пуле должен крутить вычисление и быстро отдать управление; `IO`-пул специально рассчитан на то, что поток может стоять и ждать ответа от диска или сокета.

Что делает `withContext(Dispatchers.IO) { блок }`:

1. `withContext` — это suspend-функция. Когда её вызывают, корутина приостанавливается (suspend point).
2. Рантайм планирует наш блок на `Dispatchers.IO` — найди свободный поток в IO-пуле и выполни блок там.
3. Текущий поток (например, Main) освобождается — он не ждёт; он берёт следующую задачу.
4. Когда блок отработал — рантайм возобновляет нашу корутину обратно в исходном контексте (откуда мы пришли — в Main, если из ViewModel).
5. `withContext` возвращает значение, которое вернул блок (`return@withContext` или последнее выражение).

То есть `withContext` — это **не «запусти параллельно»**, а **«временно переключись, дождись результата, вернись»**.

Очень частая путаница — `withContext` vs `launch`:

```kotlin
withContext(Dispatchers.IO) { scanTracks() }   // дожидается результата, возвращает List<Track>
launch(Dispatchers.IO) { scanTracks() }        // запускает параллельно, возвращает Job, не ждёт
```

- `withContext` — suspend-функция. Возвращает значение блока. Текущая корутина «спит» до завершения.
- `launch` — обычная функция (нужен `CoroutineScope`). Запускает новую корутину, возвращает `Job`. Текущая корутина продолжает выполнение сразу же, не дожидаясь.

В нашем сканере нам нужно дождаться списка треков, поэтому — `withContext`.

Что было бы без переключения, если убрать `withContext(Dispatchers.IO)`: `scanTracks()` сама ничего не переключает; она выполнится на том диспетчере, с которого её позвали. Если из `viewModelScope.launch { scanTracks() }` — это `Dispatchers.Main`. На устройстве с 5000 треков `query` + чтение курсора может занять секунду-две. Всё это время Compose не может перерисовывать UI (Main занят), тапы по экрану копятся в очередь, через 5 секунд Android покажет ANR-диалог. С `withContext(Dispatchers.IO)` — Main свободен, ANR не случится.

Внутри блока — собираем запрос. Сначала проекция (какие колонки нам нужны):

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt

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
```

`projection` — это «SELECT columns» для MediaStore. Передавать `null` тоже можно (вернёт все колонки), но запрашивать явно — быстрее и чётче по интенту.

Дальше — фильтр (только музыка, не короче 10 секунд):

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} > ?"
    val selectionArgs = arrayOf("10000")

    val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
```

`IS_MUSIC != 0` отсекает не-музыкальные аудио (рингтоны, нотификации). `DURATION > ?` (10000 мс) отсекает случайные короткие файлы. `?` — параметризованный запрос: значение подставляется через `selectionArgs`, а не через интерполяцию строк, что защищает от SQL-инъекций (даже если `MediaStore` к ним устойчив, привычку лучше беречь).

`COLLATE NOCASE` — сортировка без учёта регистра прямо на уровне БД. Идеально работает для латиницы, для кириллицы — не идеально (см. Шаг 8 про `lowercase()`).

Сам запрос и обход курсора:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt

    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        sortOrder,
    )?.use { c ->
        // ...работа с курсором...
    }
```

Тут две идиомы, без которых легко словить утечку — `?.` и `.use`.

`Cursor` — это **ресурс**: внутри него открытое соединение с системной БД медиа, нативная память, файловые дескрипторы. Если не вызвать `close()`, ресурс утечёт. Кроме памяти, это могут быть лимиты ОС — на Android количество одновременно открытых cursor'ов конечно.

`?.use` — комбинация двух механизмов:

- **`?.`** — safe call. `query(...)` возвращает `Cursor?` (может быть `null`, если что-то пошло не так с провайдером). `?.use` означает «если не `null` — вызови `use`».
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

Внутри `use { c -> ... }` — читаем колонки. Сначала индексы:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt

        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
```

`getColumnIndexOrThrow` — найдёт индекс колонки по имени или бросит `IllegalArgumentException`, если её нет в проекции. Это то, что нам нужно: если опечатка в `projection`, упасть здесь, а не получить `-1` и потом `getString(-1) → CursorIndexOutOfBoundsException` где-то в глубине.

Индексы вычисляем **до** цикла, а не внутри — иначе на каждом из 5000 треков будет лишний string-lookup.

Сам цикл:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt

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

`moveToNext()` сдвигает курсор на следующую строку и возвращает `true`, если она есть. Стандартный обход.

`tracks +=` на `mutableListOf` — это `list.add(...)`, безопасно (не создаёт новый список).

В конце блока — последнее выражение `tracks` (без `return@withContext` — это и есть значение, которое вернёт `withContext`).

`.orFallback(...)` — наша вспомогательная extension, разберём её сразу.

Хелперы класса:

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt

    private fun albumArtUri(albumId: Long): String =
        "content://media/external/audio/albumart/$albumId"

    private fun String?.orFallback(fallback: String): String =
        if (this.isNullOrBlank()) fallback else this
}
```

`albumArtUri` — формирует системный URI обложки альбома. Это легаси-схема, которая по-прежнему работает. На современных Android её можно заменить на `ContentUris.withAppendedId(...)`, но текущий вариант проще и читается без объяснений.

`String?.orFallback(...)` — извлекает много Kotlin-фишек одновременно:

- **Extension-функция.** Синтаксис `fun String?.orFallback(...)` означает «добавляю функцию `orFallback` ко всем выражениям типа `String?`». Под капотом — это статический метод, в который `this` передаётся первым параметром, но снаружи ты вызываешь её как метод: `someString.orFallback("...")`.
- **Receiver — `String?` (nullable).** Это важно: receiver сам может быть `null`. Внутри тела `this` — это `String?`, а не `String`. Поэтому ты можешь вызвать функцию даже на null-значении: `null.orFallback("X")` вернёт `"X"`, никакого NPE не будет.
- **`isNullOrBlank()`** — стандартная extension в stdlib, тоже на `String?`. Возвращает `true` если строка `null`, пустая или состоит только из пробелов. Это покрывает все три случая «бесполезное значение из MediaStore».
- **`else this`** — после `if (this.isNullOrBlank())` smart cast не сработает (это пользовательский предикат с точки зрения компилятора, не `if (this == null)`). Но возвращать `this` нам компилятор разрешает: тип возвращаемой функции `String`, `this` имеет тип `String?` — а компилятор уже понимает, что после `if (...isNullOrBlank()) return fallback`, оставшийся путь идёт по ветке «не null и не blank», и `this` приводится к `String` автоматически.
```

- [ ] **Step 1: Удалить старый Шаг 5**

Найти `### Шаг 5 — Android-реализация (фикс бага + actual)` и удалить до пустой строки перед `### Шаг 6`.

- [ ] **Step 2: Вставить новую версию шага**

Скопировать «Целевую структуру» выше **целиком** (от `### Шаг 5 — Android-реализация` до закрывающей `}` хелпера) на место удалённого блока.

- [ ] **Step 3: Verify — содержательные элементы из старого шага и из общего `## Разбор` на месте**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "лишняя \}","ANR","Dispatchers.IO","try \{ block","isNullOrBlank","Extension-функция","albumArtUri"
```

Все семь паттернов должны найтись.

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): restructure Шаг 5 (MusicScanner.android) + inline withContext/use/orFallback explanations"
```

---

### Task 6: Реструктурировать Шаг 6 (iOS-заглушка)

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

**Текущая структура:** Минимальный шаг — `actual class MusicScanner` с `TODO("iOS implementation: использовать MPMediaQuery.songs()")`.

**Целевая структура:**

```
### Шаг 6 — iOS-заглушка

Шаг технический: чтобы `commonMain` собирался для iOS-таргета, `actual class` нужен и там. Без него — ошибка компиляции «`expect MusicScanner` has no `actual` for iosMain». Реальную реализацию пишем не сейчас — на iOS гайд не нацелен; кладём `TODO`-заглушку.

[чанк: MusicScanner.ios.kt]

`TODO(...)` — это функция из stdlib, которая бросает `NotImplementedError`. Если кто-то попробует позвать `scanTracks()` на iOS-сборке — упадёт сразу с понятным сообщением, а не молча вернёт пустой список.

Конструктор без параметров — `iosMain` не имеет `Context` (это Android-специфичный класс). Подпись `expect class MusicScanner` это допускает: в `expect` мы не объявили primary constructor, поэтому каждая платформа решает сама.
```

- [ ] **Step 1: Удалить старый Шаг 6**

Найти `### Шаг 6 — iOS-заглушка` и удалить до пустой строки перед `### Шаг 7`.

- [ ] **Step 2: Вставить новую версию**

```markdown
### Шаг 6 — iOS-заглушка

Шаг технический: чтобы `commonMain` собирался для iOS-таргета, `actual class` нужен и там. Без него — ошибка компиляции «`expect MusicScanner` has no `actual` for iosMain». Реальную реализацию пишем не сейчас — на iOS гайд не нацелен; кладём `TODO`-заглушку.

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
```

- [ ] **Step 3: Verify**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "MusicScanner.ios.kt","TODO\(`"iOS implementation","NotImplementedError"
```

Все три паттерна должны найтись.

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): restructure Шаг 6 (iOS stub) to chunked-narrative format"
```

---

### Task 7: Реструктурировать Шаг 7 (`TracksRepositoryImpl`) + интегрировать `Разбор по строкам`

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

**Что переразлагается:**
- Текущий код-блок `TracksRepositoryImpl.kt` дробится на чанки.
- Из `#### Разбор по строкам` под этим шагом (текущие строки 303–365) каждое `##### `выражение`` встраивается в свой чанк.

**Целевая структура (полный текст вставки):**

```markdown
### Шаг 7 — `TracksRepositoryImpl`

Реактивный слой поверх сканера: хранит текущий список в `MutableStateFlow`, перезаписывает его по `refresh()`. Главная мысль шага — «один источник правды для треков, никто другой их не пишет».

Создаём файл и подключаем зависимости:

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
    // ...
}
```

`MusicScanner` приходит через конструктор — стандартный DI. Кто конкретно его создаст (Android-`actual` с `Context`) — соберёт Koin на этапе 5 гайда.

Внутреннее состояние:

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/TracksRepositoryImpl.kt

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val scanLock = Mutex()
```

`MutableStateFlow<T>` — это контейнер, который хранит **ровно одно текущее значение** типа `T` и одновременно является `Flow<T>`. Любой, кто подпишется на него, **сразу** получит текущее значение, а потом — каждое новое.

Сравни:
- Обычный `Flow<T>`: «холодная плёнка» — пока никто не вызвал `collect`, ничего не происходит. У него нет «текущего значения».
- `StateFlow<T>`: «горячее радио» — оно всегда что-то транслирует. Новый слушатель сразу слышит то, что играет прямо сейчас.

`emptyList()` — стартовое значение. Пока `refresh()` не позвали, экран увидит пустой список (а не зависнет в ожидании эмита).

Подчёркивание `_tracks` — это просто соглашение об именовании в Kotlin: «приватная мутабельная версия, наружу не показывать».

`Mutex` — это очередь корутин. Внутри `withLock { ... }` может находиться **только одна** корутина одновременно. Остальные ждут.

Ключевое отличие от `synchronized(lock) { ... }`:

| | `synchronized` | `Mutex.withLock` |
|---|---|---|
| Что блокирует | **поток** — поток встаёт и ничего не делает | **корутину** — корутина «приостанавливается» (suspend), поток освобождается и берёт другую работу |
| Откуда берётся | Java/JVM-примитив | Корутинный примитив (`kotlinx.coroutines.sync`) |
| Реентрант | Да (один поток может войти повторно) | **Нет** — повторный `withLock` из той же корутины = вечный дедлок |
| Можно из `suspend` | Можно, но плохо: занимаем поток зря | Идиоматично |

Если бы здесь стоял `synchronized`, то на время `scanTracks()` (а это запрос к MediaStore — сотни миллисекунд на большой библиотеке) мы держали бы один из 64 потоков `Dispatchers.IO` намертво. С `Mutex` поток свободен заниматься чем-то ещё, пока наша корутина «спит» в ожидании сканирования.

Наружу отдаём readonly-вариант:

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/TracksRepositoryImpl.kt

    override fun observeTracks(): Flow<List<Track>> = _tracks.asStateFlow()
```

`asStateFlow()` — это **апкаст** до `StateFlow<List<Track>>`. Возвращается тот же самый объект `_tracks`, но через тип, у которого нет setter'а. Снаружи никто не сможет вызвать `_tracks.value = ...` — только подписаться и читать.

Зачем — это инкапсуляция: единственный способ положить туда новые треки — это пройти через `refresh()`. Если бы мы отдавали `_tracks` напрямую, любой компонент мог бы перезаписать значение мимо `Mutex` и сломать инвариант «одно сканирование одновременно».

Метод обновления:

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/TracksRepositoryImpl.kt

    override suspend fun refresh() {
        scanLock.withLock {
            _tracks.value = scanner.scanTracks()
        }
    }
}
```

Модификатор `suspend` — это **обещание**: «эта функция может приостановиться». Вызвать её можно только из корутины (или из другой `suspend`-функции).

Что компилятор делает с `suspend`-функцией — он добавляет ей скрытый параметр `Continuation<T>`, который описывает «куда вернуться после паузы». Когда корутина внутри `suspend` доходит до точки приостановки (например, `withContext(IO)` или `delay(...)`), она **не блокирует поток**: поток продолжает выполнять другие корутины, а наша «засыпает», запомнив свой `Continuation`. Когда нужное событие произошло — рантайм возобновляет корутину, возможно уже на другом потоке.

Это и есть «корутина приостановилась»: не «поток встал в `Thread.sleep`», а «функция запомнила место и отдала поток обратно».

`withLock` — это `lock()` + `try { блок } finally { unlock() }`. Никакой магии: блокируется на входе, освобождается на выходе **даже при исключении**. Если корутину отменят прямо во время `scanTracks()` — лок всё равно отдадут.

Сценарий, ради которого `Mutex` и стоит: пользователь дважды нажал «Обновить» подряд.

1. Первый клик — корутина A: входит в `withLock`, блокирует mutex, начинает `scanTracks()` (это suspend, она «спит»).
2. Через 50 мс — второй клик — корутина B: входит в `refresh()`, доходит до `withLock` — mutex занят, корутина B приостанавливается прямо здесь.
3. Корутина A досканировала, присвоила `_tracks.value = ...`, вышла из `withLock`, отпустила mutex.
4. Корутина B автоматически просыпается, входит в `withLock`, начинает свой `scanTracks()`.

Без `Mutex` сценарий был бы: A и B сканируют одновременно, обе пишут в `_tracks.value`, но кто пишет последним — тот и победил. На MediaStore это не критично (запросы независимые), но уже на уровне БД или сети — гарантированный гонок.

Присваивание `.value` атомарно публикует новое значение всем подписчикам. С двумя оговорками:

- **Conflated.** Если подписчик ещё не успел обработать предыдущее значение, а пришло новое — он увидит только новое, промежуточное «потеряется». Для UI-стейта это нормально: мы хотим показать **последний** список, а не каждый промежуточный.
- **`distinctUntilChanged` встроено.** Если новое значение `equals` старому — подписчики не получат повторный эмит. Поэтому `data class Track(...)` важен (у него правильный `equals`): если список после нового сканирования совпал с предыдущим — UI не будет зря перерисовываться.
```

- [ ] **Step 1: Удалить старый Шаг 7 (включая `#### Разбор по строкам`)**

Найти `### Шаг 7 — TracksRepositoryImpl` и удалить **всё** до пустой строки перед `### Шаг 8` — это включает старый код-блок и все строки `##### `выражение``.

- [ ] **Step 2: Вставить новую версию шага**

Скопировать «Целевую структуру» выше целиком на место удалённого блока.

- [ ] **Step 3: Verify — все ключевые формулировки из старого Разбора на месте**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "холодная плёнка","горячее радио","очередь корутин","Continuation","две оговорки","Conflated","distinctUntilChanged"
```

Все семь паттернов должны найтись.

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): restructure Шаг 7 (TracksRepositoryImpl) + inline Разбор по строкам"
```

---

### Task 8: Реструктурировать Шаг 8 (`AlbumsRepositoryImpl`) + интегрировать `Разбор по строкам`

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

Самый объёмный шаг — у него самый подробный `Разбор по строкам`. Дробим максимально мелко: каждый вызов в цепочке `groupBy → map → sortedBy` — свой чанк.

**Целевая структура (полный текст вставки):**

```markdown
### Шаг 8 — `AlbumsRepositoryImpl` (группировка)

Этот класс — главный пример «делать почти ничего, но получать реактивность бесплатно». Альбомы тут нигде не хранятся в поле; они **выводятся** из текущего списка треков на лету.

Объявление и зависимости:

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
    // ...
}
```

`tracksRepository` — единственная зависимость: всё, что нам нужно, мы выведем из его потока треков.

Поток альбомов — две строки, в которых много чего:

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt

    override fun observeAlbums(): Flow<List<Album>> =
        tracksRepository.observeTracks().map { tracks -> groupIntoAlbums(tracks) }
```

Здесь два важных момента, которые сливаются в одну строку.

**`observeTracks()`** возвращает `Flow<List<Track>>`. Это «провод», по которому каждый раз, когда `TracksRepositoryImpl` обновляет свой `_tracks.value`, прилетает свежий список.

**`.map { ... }`** — это **`Flow.map`** из `kotlinx.coroutines.flow`, оператор потока. Он вешается на провод и говорит: «когда по проводу прилетит `tracks`, прогони его через эту функцию и эмитни результат дальше». Сама `groupIntoAlbums` ничего не подписывает — она вызовется **на каждый emit upstream**.

Важно не путать этот `Flow.map` с другим `.map` ниже — `items.map { it.artist }`. Имена одинаковые, но это **разные функции с разной семантикой**:

| | `Flow.map` (тут) | `List.map` (дальше) |
|---|---|---|
| Receiver | `Flow<T>` | `List<T>` |
| Когда выполняется | На каждый emit upstream-flow | Один раз, синхронно |
| Возвращает | Новый `Flow<R>` | Новый `List<R>` |
| Импорт | `kotlinx.coroutines.flow.map` | `kotlin.collections` (даже импорт не нужен) |

То, что у них одинаковое имя — это совпадение API: и потоки, и коллекции естественно поддерживают «трансформацию каждого элемента». Под капотом это абсолютно разный код.

Дальше — треки конкретного альбома:

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt

    override fun observeTracksOfAlbum(albumId: String): Flow<List<Track>> =
        tracksRepository.observeTracks().map { tracks ->
            tracks.filter { it.albumId == albumId }
                .sortedBy { it.title }
        }
```

Та же `Flow.map` снаружи, а внутри — стандартные `List.filter` и `List.sortedBy` (синхронные). Сначала отфильтровали треки этого альбома, потом отсортировали по названию.

Теперь — самое интересное, сама группировка. Цепочка из трёх вызовов на `List<Track>`. Сначала — `groupBy`:

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt

    private fun groupIntoAlbums(tracks: List<Track>): List<Album> =
        tracks
            .groupBy { it.albumId }
```

`groupBy` возвращает `Map<String, List<Track>>`. Ключ — `albumId` каждого трека, значение — список всех треков с этим `albumId`.

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

Это **`Map.map { entry -> ... }`** из stdlib (НЕ `Flow.map`!). На входе — `Map.Entry<String, List<Track>>`, на выходе — `List<Album>`.

`(albumId, items)` — это **деструктуризация**. У `Map.Entry` есть `componentN`-функции, которые позволяют записать `entry.key` и `entry.value` короче, через паттерн в скобках. Эквивалентная запись без деструктуризации:

```kotlin
.map { entry ->
    val albumId = entry.key
    val items = entry.value
    Album(...)
}
```

Внутри лямбды строим `Album` из группы треков, разбирая каждую строку.

`val artists = items.map { it.artist }.distinct()` — здесь `.map { it.artist }` — это **`List.map`** (синхронный). Из списка треков сделали список имён исполнителей.

`.distinct()` — оставляет только уникальные значения, сохраняя порядок. Алгоритм: проходит по списку, держит `LinkedHashSet`, добавляет туда; на выходе — содержимое set'а как список.

Зачем: альбом может содержать треки разных исполнителей (сборники, фит-треки). Нам важно — все ли исполнители одинаковые или нет.

`val artist = if (artists.size == 1) artists.first() else "Various Artists"` — если в альбоме все треки от одного и того же исполнителя — пишем его имя. Если от двух и более разных — стандартная пометка «Various Artists» (так делают все плееры).

`title = items.first().album` — берём название альбома из первого трека группы. Это безопасно потому, что `groupBy` гарантирует непустые списки. Если бы мы не были уверены — пришлось бы `items.firstOrNull()?.album ?: "Без названия"`.

Почему всех треков альбома спрашивать не надо: в норме у всех треков одного `albumId` поле `album` (название) совпадает. MediaStore сам это обеспечивает — `albumId` и есть хеш названия + исполнителя.

`coverUri = items.firstOrNull { it.coverUri != null }?.coverUri` — здесь хитрая деталь. Берём **первый** трек, у которого `coverUri != null` — а не просто `items.first().coverUri`.

Сценарий, ради которого это важно: альбом из 10 треков, у первых трёх MediaStore не нашёл обложку (`null`), а у четвёртого нашёл. Если бы мы взяли `items.first().coverUri` — получили бы `null` и показали бы плейсхолдер. С `firstOrNull { it.coverUri != null }` — найдём ту обложку, что есть.

Разбираем выражение по частям:
- `firstOrNull { предикат }` — возвращает первый элемент, удовлетворяющий предикату, или `null` если такого нет.
- `?.coverUri` — safe call: если результат не null, прочитай его `coverUri`; если null — оставь `null`.

`totalDurationMs = items.sumOf { it.duration }` — `sumOf` это стандартная функция-агрегат. Прогоняет лямбду по каждому элементу, складывает результаты. Эквивалентно `items.map { it.duration }.sum()`, но без промежуточного списка.

Получаем суммарную длительность альбома в миллисекундах — пригодится показать «42 трека • 3 ч 12 мин».

И финальный шаг — сортировка:

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt

            .sortedBy { it.title.lowercase() }
}
```

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
```

- [ ] **Step 1: Удалить старый Шаг 8 (включая `#### Разбор по строкам`)**

Найти `### Шаг 8 — AlbumsRepositoryImpl (группировка)` и удалить **всё** до пустой строки перед `### Шаг 9`.

- [ ] **Step 2: Вставить новую версию шага**

Скопировать «Целевую структуру» выше целиком на место удалённого блока.

- [ ] **Step 3: Verify — все ключевые формулировки и таблицы из старого Разбора на месте**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "Pink Floyd|Money|Wish You Were Here","Various Artists","деструктуризация","firstOrNull \{ предикат","sumOf","lowercase","Collator","перфекциониста","banana"
```

Все восемь паттернов должны найтись.

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): restructure Шаг 8 (AlbumsRepositoryImpl) + inline Разбор по строкам"
```

---

### Task 9: Реструктурировать Шаг 9 (Permission в манифесте)

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

**Текущая структура:** Один XML-блок и одно предложение объяснения.

**Целевая структура:**

```markdown
### Шаг 9 — Permission в манифесте

Манифест — это «декларация намерений» приложения для системы. Чтобы запросить permission в рантайме (Шаг 10), сначала надо объявить его здесь — иначе рантайм-запрос будет молча отклонён.

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

Тут два разных permission'а потому, что Google в Android 13 разделил доступ к медиа по типам: фото, видео, аудио — отдельные permission'ы. До 13 был один общий `READ_EXTERNAL_STORAGE`.

`maxSdkVersion="32"` означает: на Android 13+ это разрешение даже не запрашивается, там работает `READ_MEDIA_AUDIO`. Без этого ограничения на новых устройствах система могла бы показывать пользователю «приложение хочет доступ к файлам», что и пугает, и не работает (даст storage без аудио).
```

- [ ] **Step 1: Удалить старый Шаг 9**

Найти `### Шаг 9 — Permission в манифесте` и удалить до пустой строки перед `### Шаг 10`.

- [ ] **Step 2: Вставить новую версию**

Скопировать «Целевую структуру» выше целиком.

- [ ] **Step 3: Verify**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "READ_MEDIA_AUDIO","maxSdkVersion=`"32`"","разделил доступ к медиа"
```

Все три паттерна должны найтись.

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): restructure Шаг 9 (manifest permissions) to chunked-narrative format"
```

---

### Task 10: Реструктурировать Шаг 10 (Compose-обёртка) + интегрировать `Разбор по строкам` + инлайн `Build.VERSION.SDK_INT`

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

**Что переразлагается:**
- Текущий код-блок `AudioPermission.kt` дробится на чанки.
- Из `#### Разбор по строкам — rememberAudioPermissionState` (под этим шагом, текущие строки 631–715) каждое `##### `выражение`` встраивается в свой чанк.
- Из общего `## Разбор` подсекция `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)` (текущие строки 842–850) встраивается в чанк, где появляется эта проверка.
- Также сохраняем второй пример использования — `TracksScreen` с `LaunchedEffect` (строки 610–629) — как отдельный финальный чанк с короткой подписью.

**Целевая структура (полный текст вставки):**

```markdown
### Шаг 10 — Compose-обёртка для запроса разрешения

Activity Result API — современная замена `onActivityResult`. Идея: «я хочу запустить системный экран и получить результат, при этом не зависеть от lifecycle Activity вручную». Compose-обёртка `rememberLauncherForActivityResult` делает это идиоматично из любого `@Composable`.

Создаём файл и описываем тип состояния:

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
```

Здесь — `enum`, а не `sealed interface`, потому что у нас три **константных** состояния, каждое без собственных данных. Если бы понадобилось «`Denied(reason: String)`» — пришлось бы переключаться на `sealed interface`, потому что у `enum` нет полей-переменных.

Сама composable-функция, шапка:

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/permissions/AudioPermission.kt

@Composable
fun rememberAudioPermissionState(): Pair<AudioPermissionState, () -> Unit> {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    // ...
}
```

`LocalContext` — это `CompositionLocal`, специальный механизм Compose, который позволяет передать значение «вниз по дереву композиций» без явной передачи через параметры. Корневой `setContent { ... }` положил туда текущий `Context`, а любой Composable ниже может его взять через `.current`.

Думай об этом как о неявной переменной в области видимости: «во всём поддереве — этот Context».

Дальше — выбор имени permission'а от версии Android. Это та же история, что мы объявили в манифесте: API 33+ и API 32-, разные имена.

Android не даёт одну универсальную константу для «читать аудио». На API 33+ это `READ_MEDIA_AUDIO`, раньше — `READ_EXTERNAL_STORAGE`. Проверка по `Build.VERSION.SDK_INT` — стандартный способ рантайм-совместимости.

`Build.VERSION_CODES.TIRAMISU` — это просто `33`, константа из SDK. Имена-кодовые («Tiramisu», «UpsideDownCake») выпускаются вместе с релизом Android: 33 = Tiramisu = Android 13.

Что было бы без проверки: попытка запросить `Manifest.permission.READ_MEDIA_AUDIO` на Android 12 (API 32) — система **не знает** такого permission, в манифесте его не объявить как обязательный (или объявить, но он будет проигнорирован). А `READ_EXTERNAL_STORAGE` на Android 13+ перестал давать доступ к аудио — Google разделил пермишены по типам медиа. Поэтому строго: 33+ = `READ_MEDIA_AUDIO`, ниже = `READ_EXTERNAL_STORAGE`.

Манифест и runtime-проверка должны совпадать — иначе на одной из версий разрешение не сработает.

Состояние permission'а:

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/permissions/AudioPermission.kt

    var state by remember {
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED
            ) AudioPermissionState.Granted
            else AudioPermissionState.Unknown
        )
    }
```

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

Внутри `mutableStateOf(...)` мы инициализируем стартовое значение: если permission уже выдан (это бывает на повторных запусках) — `Granted`, иначе `Unknown` (ещё не спросили). `ContextCompat.checkSelfPermission` — синхронная проверка, без всяких диалогов; просто «есть/нет прямо сейчас».

Регистрируем launcher для запроса:

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/permissions/AudioPermission.kt

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        state = if (granted) AudioPermissionState.Granted else AudioPermissionState.Denied
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

`contract = ActivityResultContracts.RequestPermission()` — это «шаблон взаимодействия»: вход — `String` (имя permission), выход — `Boolean` (granted). Под капотом контракт умеет упаковать вход в Intent и распаковать результат. Контракты есть и на другие сценарии: `PickVisualMedia`, `TakePicture`, `OpenDocument`.

Стабильная функция-триггер для UI:

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/permissions/AudioPermission.kt

    val request = remember(launcher) { { launcher.launch(permission) } }

    return state to request
}
```

Тут **двойные фигурные** в одну строку — две разных конструкции одна в одной.

Внешние скобки — лямбда, которую `remember` запоминает (это её провайдер начального значения).
Внутренние скобки — лямбда, которую мы хотим **сохранить как значение**: `() -> Unit`, которая при вызове запустит permission request.

Развёрнуто это выглядит так:

```kotlin
val request: () -> Unit = remember(launcher) {
    val capturedLauncher = launcher
    val capturedPermission = permission
    return@remember { capturedLauncher.launch(capturedPermission) }
}
```

`remember(launcher)` с **ключом** означает: «запомни значение, пока ключ `launcher` не поменялся». Если `launcher` пересоздастся (теоретически — после Activity recreate) — лямбда `request` тоже пересоздастся с новым launcher внутри.

Зачем это вообще: если бы мы передавали `{ launcher.launch(permission) }` напрямую в дочерний Composable, Compose видел бы «новую лямбду на каждой рекомпозиции» и считал бы дочерние Composable нестабильными → лишние рекомпозиции. С `remember` лямбда стабильная — ссылка одна и та же.

`return state to request` — `a to b` это инфиксная функция-фабрика для `Pair<A, B>`. Эквивалентно `Pair(state, request)`.

В вызывающем коде это раскладывается обратно через деструктуризацию: `val (permissionState, requestPermission) = rememberAudioPermissionState()`. Та же механика, что у `(albumId, items)` в `groupBy` — `Pair` тоже имеет `component1`/`component2`.

Использование на экране:

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/.../TracksScreen.kt

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

Подробно `TracksScreen` и `TracksViewModel` разберём в файлах `06` и `07`. Здесь главное — увидеть полную цепочку: composable получает текущее состояние и функцию-триггер; `LaunchedEffect` стартует загрузку при `Granted`; `when` показывает один из трёх UI в зависимости от состояния.
```

- [ ] **Step 1: Удалить старый Шаг 10 (включая `#### Разбор по строкам — rememberAudioPermissionState`)**

Найти `### Шаг 10 — Compose-обёртка для запроса разрешения` и удалить **всё** до пустой строки перед `---` (горизонтальная линия перед `## Разбор`).

- [ ] **Step 2: Вставить новую версию шага**

Скопировать «Целевую структуру» выше целиком.

- [ ] **Step 3: Verify — все ключевые формулировки на месте**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "enum class AudioPermissionState","CompositionLocal","mutableStateOf","property delegation","ActivityResultContracts","Tiramisu","Build.VERSION.SDK_INT","componentN|component1","LaunchedEffect"
```

Все девять паттернов должны найтись.

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): restructure Шаг 10 (Compose permission) + inline Разбор + Build.VERSION"
```

---

### Task 11: Удалить общий `## Разбор`

После Task 5, 7, 8, 10 весь содержательный материал из общего `## Разбор` уже мигрировал в шаги (см. таблицу в начале плана). Теперь удаляем сам раздел — он стал пустым по смыслу.

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

- [ ] **Step 1: Найти и удалить раздел `## Разбор`**

В файле найти `## Разбор` (строка 719 в исходном файле; после правок — другая, ищи по тексту). Удалить от заголовка `## Разбор` (включая горизонтальную линию `---` перед ним) и до следующей горизонтальной линии `---` перед `## Подводные камни`.

После удаления соседними должны оказаться:

```
[конец последнего чанка Шага 10]

---

## Подводные камни
```

- [ ] **Step 2: Verify — раздела `## Разбор` больше нет**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "^## Разбор$"
```

Должно вернуть **пустой результат** (ноль матчей). Если что-то нашлось — Step 1 удалил не до конца.

- [ ] **Step 3: Verify — содержимое старого `## Разбор` всё ещё в файле (перенесено в шаги)**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "withContext","Dispatchers.IO","cursor.use|cursor\?.use","orFallback","Mutex.withLock","Flow.map","List.map","Tiramisu"
```

Все восемь паттернов должны найтись (содержание мигрировало в шаги, не пропало).

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): remove superseded ## Разбор section (content migrated to steps)"
```

---

### Task 12: Добавить `## Готовые файлы (для копи-паста)` в самый конец перед `## Дальше`

**Files:**
- Modify: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

**Где вставить:** После раздела `## Try yourself` и перед `## Дальше`. Это будет последний содержательный раздел этапа.

**Состав раздела:** По одному код-блоку на каждый создаваемый/изменяемый файл этапа, в порядке появления в шагах:

1. `Track.kt` (Шаг 1)
2. `Album.kt` (Шаг 2)
3. `TracksRepository.kt` (Шаг 3)
4. `AlbumsRepository.kt` (Шаг 3)
5. `MusicScanner.kt` — commonMain `expect` (Шаг 4)
6. `MusicScanner.android.kt` — Android `actual` (Шаг 5)
7. `MusicScanner.ios.kt` — iOS-заглушка (Шаг 6)
8. `TracksRepositoryImpl.kt` (Шаг 7)
9. `AlbumsRepositoryImpl.kt` (Шаг 8)
10. `AndroidManifest.xml` (Шаг 9 — XML, не Kotlin)
11. `AudioPermission.kt` (Шаг 10)

`build.gradle.kts` (изменение из Шага 1) — **не включаем**: это не самостоятельный файл этапа, изменение касается одной зависимости и не имеет смысла копировать его весь.

`TracksScreen.kt` из второго примера в Шаге 10 — **не включаем**: это иллюстрация, реальная реализация будет в этапе 7.

- [ ] **Step 1: Найти место вставки**

В файле найти `## Try yourself` и его конец (последняя строка до `---` перед `## Дальше`). Вставка идёт сразу после горизонтальной линии `---`, до заголовка `## Дальше`.

- [ ] **Step 2: Вставить раздел**

```markdown
## Готовые файлы (для копи-паста)

> Если запутался по дороге или прыгнул через шаг — здесь полные финальные версии всех файлов, созданных или изменённых в этом этапе. Копируй целиком; объяснения — в учебной части выше.

### `shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/Track.kt`

```kotlin
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

### `shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/Album.kt`

```kotlin
package org.example.mp3player.domain

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val coverUri: String?,
    val totalDurationMs: Long,
)
```

### `shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/TracksRepository.kt`

```kotlin
package org.example.mp3player.domain

import kotlinx.coroutines.flow.Flow

interface TracksRepository {
    /** Текущий список треков. Переэмитит после вызова [refresh]. */
    fun observeTracks(): Flow<List<Track>>

    /** Запускает пересканирование. Подписчики [observeTracks] получат новый список. */
    suspend fun refresh()
}
```

### `shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/AlbumsRepository.kt`

```kotlin
package org.example.mp3player.domain

import kotlinx.coroutines.flow.Flow

interface AlbumsRepository {
    /** Альбомы, сгруппированные по albumId. Обновляется при изменении списка треков. */
    fun observeAlbums(): Flow<List<Album>>

    /** Треки конкретного альбома, в порядке номера дорожки (пока просто по title). */
    fun observeTracksOfAlbum(albumId: String): Flow<List<Track>>
}
```

### `shared/data/src/commonMain/kotlin/org/example/mp3player/data/MusicScanner.kt` (commonMain `expect`)

```kotlin
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

### `shared/data/src/androidMain/kotlin/org/example/mp3player/data/MusicScanner.android.kt`

```kotlin
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

### `shared/data/src/iosMain/kotlin/org/example/mp3player/data/MusicScanner.ios.kt`

```kotlin
package org.example.mp3player.data

import org.example.mp3player.domain.Track

actual class MusicScanner {
    actual suspend fun scanTracks(): List<Track> {
        TODO("iOS implementation: использовать MPMediaQuery.songs()")
    }
}
```

### `shared/data/src/commonMain/kotlin/org/example/mp3player/data/TracksRepositoryImpl.kt`

```kotlin
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
        scanLock.withLock {
            _tracks.value = scanner.scanTracks()
        }
    }
}
```

### `shared/data/src/commonMain/kotlin/org/example/mp3player/data/AlbumsRepositoryImpl.kt`

```kotlin
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

### `composeApp/src/androidMain/AndroidManifest.xml`

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

### `composeApp/src/androidMain/kotlin/org/example/mp3player/permissions/AudioPermission.kt`

```kotlin
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
```

- [ ] **Step 3: Verify — раздел появился, все 11 файлов на месте**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "^## Готовые файлы","### ``shared/domain/.+/Track.kt","### ``shared/domain/.+/Album.kt","### ``shared/domain/.+/TracksRepository.kt","### ``shared/domain/.+/AlbumsRepository.kt","### ``shared/data/.+/MusicScanner.kt","### ``shared/data/.+/MusicScanner.android.kt","### ``shared/data/.+/MusicScanner.ios.kt","### ``shared/data/.+/TracksRepositoryImpl.kt","### ``shared/data/.+/AlbumsRepositoryImpl.kt","### ``composeApp/.+/AndroidManifest.xml","### ``composeApp/.+/AudioPermission.kt"
```

Все 12 паттернов должны найтись (заголовок раздела + 11 файлов).

- [ ] **Step 4: Commit**

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): add ## Готовые файлы (для копи-паста) appendix to 02-PERMISSIONS_AND_SCAN.md"
```

---

### Task 13: Финальная верификация всего файла

После всех правок — единая проверка, что:
1. Структура файла соответствует целевой.
2. Никакие важные формулировки из старых разборов не пропали.
3. Не осталось обломков старой структуры (`#### Разбор по строкам`).

**Files:**
- Read: `mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md`

- [ ] **Step 1: Структура заголовков H2/H3 — соответствует целевой**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "^##? "
```

Ожидаемая последовательность (строго):
```
# 02. Разрешения и сканирование музыки
## Зачем
## Что реализуем
## Реализация
### Шаг 1 — Перенести `Track` в `domain`
### Шаг 2 — Добавить `Album` в `domain`
### Шаг 3 — Интерфейсы репозиториев
### Шаг 4 — `expect` MusicScanner
### Шаг 5 — Android-реализация (фикс бага + `actual`)
### Шаг 6 — iOS-заглушка
### Шаг 7 — `TracksRepositoryImpl`
### Шаг 8 — `AlbumsRepositoryImpl` (группировка)
### Шаг 9 — Permission в манифесте
### Шаг 10 — Compose-обёртка для запроса разрешения
## Подводные камни
## Try yourself
## Готовые файлы (для копи-паста)
## Дальше
## Ссылки
```

Если в результате есть лишний `## Разбор` или какой-нибудь `#### Разбор по строкам` — что-то осталось от старой структуры, надо удалить.

- [ ] **Step 2: Финальный grep по «никогда не должно остаться»**

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "^#### Разбор по строкам","^## Разбор$"
```

Должно вернуть **пустой результат**. Если что-то нашлось — старая структура не вычищена.

- [ ] **Step 3: Финальный grep по «должно остаться» (master-список ключевых формулировок)**

Эти фразы — содержательные элементы, перенесённые из старых разборов. Каждая должна найтись хотя бы один раз:

```powershell
Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "холодная плёнка","горячее радио","Continuation","очередь корутин","Conflated","distinctUntilChanged","Pink Floyd|Money|Wish You Were Here","Various Artists","Collator","перфекциониста","CompositionLocal","property delegation","Tiramisu","ANR","try \{","NotImplementedError"
```

Все 16 паттернов должны найтись.

- [ ] **Step 4: Маркеры-пути есть в чанках кода**

Беглый sanity check: код-блоки в учебной части (НЕ в приложении!) должны начинаться с комментария-пути. В приложении — нет (там пути в заголовках H3).

```powershell
(Select-String -Path mp3ppl\docs\guide\02-PERMISSIONS_AND_SCAN.md -Pattern "^// shared/|^// composeApp/|^<!-- composeApp/").Count
```

Должно быть **минимум 25** матчей (примерная нижняя граница: каждый шаг даёт 1–4 чанка, итого ~30+ маркеров в учебной части).

- [ ] **Step 5: Финальный коммит верификации (если ничего не правил — пропустить)**

Если Steps 1–4 нашли ошибки и пришлось править — закоммить:

```powershell
git add mp3ppl/docs/guide/02-PERMISSIONS_AND_SCAN.md
git commit -m "docs(guide): final cleanup after restructure verification"
```

Если все проверки прошли с первого раза — ничего не коммитить, в этом таске только верификация.

- [ ] **Step 6: Финальный отчёт**

В терминал отчитаться о результатах:
- Всего шагов реструктурировано: **10**.
- Удалён общий раздел: **`## Разбор`**.
- Добавлен новый раздел: **`## Готовые файлы (для копи-паста)`** с 11 файлами.
- Размер файла до/после (в строках) — для иллюстрации, не критерий.

---

## Self-Review

### Spec coverage

| Пункт спеки | Где реализовано |
|---|---|
| Каждый шаг переписан в формат «нарезка-нарратив» | Tasks 1–10 |
| Маркер-путь `// shared/...` в каждом чанке | Все Tasks 1–10 (явно в шаблоне), Task 13 Step 4 (verify) |
| Связки-нарратив между чанками | Tasks 1–10 (явно в текстах вставок) |
| `#### Разбор по строкам` исчезает как структура | Tasks 7, 8, 10 (удаляют под-разделы при переписывании); Task 13 Step 2 (verify) |
| Содержание старых разборов не теряется | Tasks 7, 8, 10 (явные redistributed тексты); Task 13 Step 3 (master-grep по ключевым фразам) |
| Раздел `## Готовые файлы (для копи-паста)` в конце | Task 12 |
| Состав приложения: только финальные версии | Task 12 (явные коды файлов, в порядке появления) |
| Стиль (русский, «ты», термины латиницей) | Принципы для всех задач |
| Архитектура и порядок шагов не меняются | Не трогаем заголовки шагов (только содержимое) |

### Placeholder scan

- Никаких "TBD/TODO/допишу позже" в плане нет.
- Все код-блоки в задачах содержат полный текст вставки.
- Все verify-команды конкретные и исполнимые.
- Никаких "Similar to Task N" — каждая задача автономна (повторяющиеся фразы про маркер-путь дублируются осознанно).

### Type consistency

- Имена классов и методов везде совпадают с реальным кодом гайда (`MusicScanner`, `TracksRepositoryImpl`, `AlbumsRepositoryImpl`, `rememberAudioPermissionState`, `AudioPermissionState`).
- Пути `shared/data/src/commonMain/kotlin/org/example/mp3player/data/...` и `shared/domain/src/commonMain/kotlin/org/example/mp3player/domain/...` единообразны во всех задачах.
- Сигнатуры (`fun observeAlbums(): Flow<List<Album>>`, `suspend fun refresh()` и т.д.) совпадают между интерфейсом (Task 3) и реализациями (Tasks 7, 8) и приложением (Task 12).

### Дополнительная проверка — `## Разбор` сквозной раздел

Подсекции из строк 717–851 исходного файла:
- `withContext(Dispatchers.IO) — что физически происходит` → Task 5 (полностью).
- `withContext vs launch` → Task 5 (полностью).
- `cursor?.use { ... }` → Task 5 (полностью).
- `String?.orFallback(fallback)` → Task 5 (полностью).
- `Mutex.withLock` (cross-ref на Шаг 7) → Task 7 (полностью встроено в чанки).
- `Flow.map vs List.map` (cross-ref на Шаг 8) → Task 8 (полностью встроено).
- `groupBy { it.albumId }` (cross-ref на Шаг 8) → Task 8 (полностью встроено).
- `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)` → Task 10 (полностью).

Все 8 подсекций имеют новый дом → Task 11 (удаление `## Разбор`) безопасен.
