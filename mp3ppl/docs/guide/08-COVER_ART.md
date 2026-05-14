# 08. Обложки альбомов

## Зачем

К этому моменту приложение работает: треки сканируются, альбомы группируются, плеер играет, экраны переключаются. Но один штрих превращает сырой UI в продукт — **обложки**. Без них всё выглядит как Проводник, а не как плеер.

Источники обложек на Android:

1. **`content://media/external/audio/albumart/{albumId}`** — MediaStore сам извлекает обложку из ID3 при индексации. Работает для большинства треков. **Это то, что мы уже сохранили в `Track.coverUri`.**
2. **`Track.path`** напрямую — Coil через `MediaMetadataRetriever` сможет достать embedded-картинку. Fallback, если вариант 1 не сработал.
3. **Поиск по альбому через Last.fm / MusicBrainz API** — для треков без встроенных обложек. Внешняя сеть → отдельная задача, выходит за рамки гайда.

Для нашего MVP хватит первого варианта плюс красивый плейсхолдер, когда обложки нет.

---

## Что реализуем

1. Подключим Coil3 с поддержкой KMP.
2. Настроим `ImageLoader` с дисковым и памятным кэшем.
3. Сделаем `ImageLoader` синглтоном через Koin.
4. Обёртку `CoverImage` с плейсхолдером и error-fallback.
5. `expect class CoverArtReader` для ручного извлечения (понадобится, если обложки нет в `content://albumart`, например, для пользовательских альбомов).
6. Кэширование custom-обложек пользовательских альбомов в файловой системе.

Новые файлы:

```
shared/data/src/
├── commonMain/kotlin/org/example/mp3player/data/art/
│   ├── CoverArtReader.kt                     (expect)
│   └── CoverArtStore.kt                      (кэширование custom-обложек)
├── androidMain/kotlin/org/example/mp3player/data/art/
│   ├── CoverArtReader.android.kt             (actual, MediaMetadataRetriever)
│   └── CoverArtStoreAndroid.kt               (actual-логика сохранения файлов)
└── iosMain/kotlin/org/example/mp3player/data/art/
    └── CoverArtReader.ios.kt                 (заглушка)

shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/common/
└── CoverImage.kt                             (универсальная AsyncImage-обёртка)

composeApp/src/androidMain/kotlin/org/example/mp3player/
└── ImageLoaderFactory.kt                     (Coil ImageLoader с настройками)
```

---

## Реализация

### Шаг 1 — Зависимости

**Coil3 vs Coil2** — пакеты разные. Coil3 — полностью переписан под KMP; Coil2 был Android-only.

| Coil2 | Coil3 |
|---|---|
| `io.coil-kt:coil-compose` | `io.coil-kt.coil3:coil-compose` |
| `import coil.compose.AsyncImage` | `import coil3.compose.AsyncImage` |

Будь осторожен с туториалами — если видишь `import coil.compose.AsyncImage` (без 3), это Coil2 и не подойдёт.

Добавляем версии и артефакты:

```toml
[versions]
coil = "3.4.0"
okio = "3.9.0"

[libraries]
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-ktor = { group = "io.coil-kt.coil3", name = "coil-network-ktor3", version.ref = "coil" }   # опционально для сети
okio = { group = "com.squareup.okio", name = "okio", version.ref = "okio" }
```

`shared/presentation/build.gradle.kts`:

```kotlin
commonMain.dependencies {
    implementation(libs.coil.compose)
}
```

`shared/data/build.gradle.kts`:

```kotlin
commonMain.dependencies {
    implementation(libs.okio)
}
```

### Шаг 2 — Кастомный `ImageLoader`

По умолчанию Coil сам создаст стандартный `ImageLoader`, но кэш будет минимальный. Настроим явно.

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/ImageLoaderFactory.kt
package org.example.mp3player

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

fun createImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .crossfade(true)
    .memoryCache {
        MemoryCache.Builder()
            .maxSizePercent(context, 0.25)   // 25% доступной памяти
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("cover_art").toOkioPath())
            .maxSizeBytes(50L * 1024 * 1024)  // 50 MB
            .build()
    }
    .memoryCachePolicy(CachePolicy.ENABLED)
    .diskCachePolicy(CachePolicy.ENABLED)
    .build()
```

**Что такое `ImageLoader` и почему он один на приложение.** Это **главный объект Coil**. В нём живут memory-cache (LRU в RAM), disk-cache (LRU на диске), executor для загрузки, сетевые транспорты, декодеры форматов (PNG/JPEG/GIF/WebP/SVG). Один `ImageLoader` обслуживает все `AsyncImage` в приложении — если бы их было два, кэши дублировались бы. Один синглтон на процесс.

**`MemoryCache.maxSizePercent(context, 0.25)`** — «возьми 25% от `Runtime.maxMemory()` приложения и не превышай». Численный пример: на обычном Android-устройстве `maxMemory` ~256 MB, 25% = **64 MB**. Coil сам ужимает обложки до размера `AsyncImage` (если в UI 56dp обложка, в кэш ляжет картинка 56dp, не оригинал 1024×1024), поэтому памяти хватает с запасом. При нехватке Coil выкидывает наименее используемые (LRU).

**`crossfade(true)`** — при переходе из `Loading` в `Success` Coil анимирует `alpha` нового изображения от 0 до 1 за ~100 мс (старая картинка одновременно уходит в 0). Без — обновление мгновенное и «дёрганое». `crossfade(durationMillis = 200)` — настройка длительности.

**`okio.Path` и `toOkioPath()`.** Coil3 не использует `java.io.File` напрямую — он использует `okio.Path` (Square Okio). Зачем: Coil3 это KMP-библиотека, на iOS нет `java.io.File`. Okio даёт единый Path-API, который маппится в `java.io.File` на JVM, в NSURL на iOS и т.п. `File.toOkioPath()` — extension из okio, конвертирующий `java.io.File` → `okio.Path`. На Android это просто обёртка вокруг абсолютного пути; для KMP-совместимости.

### Шаг 3 — Установка `ImageLoader` глобально

```kotlin
// composeApp/src/androidMain/kotlin/org/example/mp3player/App.kt
package org.example.mp3player

import android.app.Application
import coil3.SingletonImageLoader
import org.example.mp3player.data.di.androidDataModule
import org.example.mp3player.data.di.dataModule
import org.example.mp3player.presentation.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@App)
            modules(dataModule, androidDataModule, presentationModule)
        }

        // Глобальный ImageLoader — Coil будет использовать его для всех AsyncImage.
        SingletonImageLoader.setSafe { createImageLoader(this) }
    }
}
```

**`SingletonImageLoader.setSafe { ... }` — лямбда-провайдер.** Сигнатура:

```kotlin
fun setSafe(factory: SingletonImageLoader.Factory)

fun interface Factory {
    fun newImageLoader(context: PlatformContext): ImageLoader
}
```

`setSafe { ... }` принимает лямбду-фабрику. Она **НЕ вызывается сразу** — Coil сохраняет лямбду и вызывает её при первом обращении к глобальному ImageLoader (например, при первом `AsyncImage`). Lazy-инициализация: ImageLoader создаёт пулы потоков, открывает disk cache, читает индекс — несколько мс. Если пользователь не открывает экраны с картинками сразу — экономим эти мс на старте.

`setSafe` vs `set`: `set(factory)` упадёт с `IllegalStateException`, если фабрика уже была установлена. `setSafe(factory)` тихо игнорирует повторный вызов — спасает при инструментальных тестах, где Application пересоздаётся.

### Шаг 4 — Обёртка `CoverImage`

Одна и та же логика показа обложки нужна в списке треков, на карточке альбома, на экране плеера. Делаем переиспользуемый компонент:

```kotlin
// shared/presentation/src/commonMain/kotlin/org/example/mp3player/presentation/common/CoverImage.kt
package org.example.mp3player.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest

@Composable
fun CoverImage(
    data: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (data == null) {
            Placeholder()
        } else {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(data)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { state ->
                    // При ошибке нарисуем placeholder. Ничего делать не надо —
                    // Box сам покажет фон, а AsyncImage скроется.
                },
            )
        }
    }
}

@Composable
private fun Placeholder() {
    Icon(
        imageVector = Icons.Default.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

Использование:
```kotlin
CoverImage(
    data = track.coverUri,
    contentDescription = track.title,
    size = 56.dp,
)
```

**`contentScale = ContentScale.Crop`** — картинка разного соотношения сторон не искажается, а кропается до квадрата. Для обложек — стандарт.

**`MaterialTheme.colorScheme.surfaceVariant`** — один из «фоновых» цветов Material 3, чуть отличающийся от `surface`. Material рекомендует для «приподнятых» поверхностей (карточки, плейсхолдеры). Цвет адаптируется к light/dark теме автоматически. `MaterialTheme.colorScheme.*` доступен только внутри `@Composable` (это `CompositionLocal`) — нельзя сохранить в обычной константе.

**Coil cache key — как Coil решает «грузить или взять из кэша».** Coil идентифицирует загрузку по **ключу**, который выводится из `model` (то, что ты передал в `AsyncImage`). Для `String` URL — ключ это сама строка; для `Uri` — `uri.toString()`; для `File` — абсолютный путь. Два разных `AsyncImage` с одинаковым `model` → один ключ → одна загрузка в фоне, оба получат результат.

Подвох: если ты заменил содержимое файла (пользователь сменил обложку альбома, мы перезаписали `42.jpg`), но путь остался прежним — Coil **не узнает**, покажет старую закэшированную версию. Решение для нашего MVP — добавлять версию в URI при смене обложки: `path?v=1` → `path?v=2`. Coil увидит новый ключ → загрузит заново.

### Шаг 5 — `expect class CoverArtReader`

Нужен для случаев, когда MediaStore не достал обложку (редко, но бывает — особенно для .flac и свежезаписанных файлов).

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/art/CoverArtReader.kt
package org.example.mp3player.data.art

expect class CoverArtReader {
    /**
     * Пытается извлечь embedded-обложку из аудиофайла.
     * @param path путь к файлу (или content:// URI).
     * @return сырые байты картинки или null, если нет.
     */
    suspend fun readEmbedded(path: String): ByteArray?
}
```

### Шаг 6 — Android actual через `MediaMetadataRetriever`

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/art/CoverArtReader.android.kt
package org.example.mp3player.data.art

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class CoverArtReader(private val context: Context) {

    actual suspend fun readEmbedded(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (path.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(path))
            } else {
                retriever.setDataSource(path)
            }
            retriever.embeddedPicture
        } catch (e: Exception) {
            // Битый файл, нет прав и т.п. — просто нет обложки.
            null
        } finally {
            retriever.release()
        }
    }
}
```

**`MediaMetadataRetriever` — нативный объект.** Это Java-обёртка над **native C++ объектом**, который умеет парсить медиа-контейнеры (MP4, MP3, FLAC и т.д.). Native-часть держит file descriptor, буферы парсинга, декодеры. Это всё **не управляется GC**. Если потерял ссылку без `release()` — нативные ресурсы остаются. Через несколько сотен таких утечек — ANR, OOM, или система перестанет давать новые file descriptor.

`try/finally` обязателен. Альтернатива — `.use { }` (с API 29 `MediaMetadataRetriever` реализует `AutoCloseable`).

`embeddedPicture` читает байты обложки из метаданных. Возвращает `ByteArray?` (null если обложки нет). Внутри — парсинг ID3-тега для MP3, MP4 atoms для M4A, Vorbis comment для FLAC.

Альтернатива — `MediaStore.Audio.Media.AlbumArt` (основной источник). Она быстрее, потому что MediaStore уже распарсил все треки при индексации. `MediaMetadataRetriever` используется как **fallback**, когда MediaStore по какой-то причине не нашёл обложку.

### Шаг 7 — iOS заглушка

```kotlin
// shared/data/src/iosMain/kotlin/org/example/mp3player/data/art/CoverArtReader.ios.kt
package org.example.mp3player.data.art

actual class CoverArtReader {
    actual suspend fun readEmbedded(path: String): ByteArray? {
        TODO("iOS: AVAsset.metadata через AVURLAsset")
    }
}
```

### Шаг 8 — Сохранение пользовательских обложек

Когда пользователь выбирает картинку для своего альбома из галереи, нам нужно:
1. Скопировать её к себе (URI из галереи может стать невалидным после перезагрузки устройства).
2. Сохранить в кэше с устойчивым именем.
3. Запомнить путь в Room.

```kotlin
// shared/data/src/commonMain/kotlin/org/example/mp3player/data/art/CoverArtStore.kt
package org.example.mp3player.data.art

expect class CoverArtStore {
    /**
     * Сохраняет переданные байты обложки в кэш приложения.
     * @return путь к сохранённому файлу для последующей загрузки через Coil.
     */
    suspend fun save(albumId: Long, bytes: ByteArray): String

    suspend fun delete(albumId: Long)
}
```

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/art/CoverArtStoreAndroid.kt
package org.example.mp3player.data.art

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class CoverArtStore(private val context: Context) {

    private val dir: File by lazy {
        File(context.filesDir, "user_album_covers").apply { mkdirs() }
    }

    actual suspend fun save(albumId: Long, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val file = File(dir, "$albumId.jpg")
        file.writeBytes(bytes)
        file.absolutePath
    }

    actual suspend fun delete(albumId: Long) = withContext(Dispatchers.IO) {
        File(dir, "$albumId.jpg").delete()
        Unit
    }
}
```

**Почему `filesDir`, а не `cacheDir`?** `cacheDir` Android может чистить в любой момент, когда ему нужно место — пользователь увидит свои альбомы без обложек, баг. `filesDir` — только при удалении приложения. Для пользовательских данных (треки физически лежат на устройстве, MediaStore их кэширует сам; пользовательские обложки — наши данные, система их не знает) — `filesDir`.

**Чтение из URI: `context.contentResolver.openInputStream(uri)?.use { it.readBytes() }`** (понадобится при копировании обложки из галереи) содержит несколько идиом:

1. `openInputStream(uri)` — возвращает `InputStream?` (null если URI невалидный или нет прав).
2. `?.use { ... }` — safe call + `Closeable.use`. Если stream не null — открой блок, после блока (или при исключении) автоматически закрой.
3. `it.readBytes()` — extension Kotlin, читает stream до конца, возвращает `ByteArray`.
4. Результат всего выражения: `ByteArray?`.

Та же идиома, что для `Cursor` в файле 02 (см. там подробный разбор `?.use`).

**`PickVisualMedia` — что под капотом.** «Системный photo picker», добавлен в Android 13 (API 33) как часть privacy-улучшений. На API 33+ запускается отдельная Activity Google Photo Picker, которая показывает галерею **без необходимости разрешения `READ_MEDIA_IMAGES`** — пользователь сам выбирает, что хочет дать. На API 30-32 — через Google Play Services аналогичный picker. На API <30 — автоматический fallback на `ACTION_GET_CONTENT`.

`PickVisualMediaRequest` принимает фильтр: `ImageOnly`, `VideoOnly`, `ImageAndVideo`. Возвращает `Uri?` через callback. URI имеет temporary grant — работает, пока процесс жив. Поэтому мы **сразу копируем байты** в свой `filesDir`: после kill процесса URI станет невалидным.

### Шаг 9 — Регистрация в Koin

```kotlin
// shared/data/src/androidMain/kotlin/org/example/mp3player/data/di/AndroidDataModule.kt

val androidDataModule = module {
    // ... существующие

    single { CoverArtReader(androidContext()) }
    single { CoverArtStore(androidContext()) }
}
```

### Шаг 10 — Использование с пользовательскими альбомами

В `UserAlbumsRepositoryImpl.setCover`:

```kotlin
override suspend fun setCover(id: Long, coverBytes: ByteArray?) {
    val coverPath = if (coverBytes == null) {
        coverArtStore.delete(id)
        null
    } else {
        coverArtStore.save(id, coverBytes)
    }
    val current = dao.observeById(id).firstValue() ?: return
    dao.updateAlbum(current.album.copy(coverUri = coverPath))
}
```

На экране `UserAlbumDetailsScreen`:

```kotlin
val context = LocalContext.current
val pickImage = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes != null) viewModel.onEvent(UserAlbumDetailsEvent.SetCover(bytes))
    }
}

Button(onClick = {
    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
}) {
    Text("Выбрать обложку")
}
```

### Шаг 11 — Обновить корневой `GUIDE.md`

Последним шагом — обновить корневой `GUIDE.md`, чтобы он отсылал к roadmap.


## Подводные камни

### 1. Coil2 vs Coil3
Случайно подключил `io.coil-kt:coil-compose` (без 3) → на iOS не собирается. Всегда `io.coil-kt.coil3:coil-compose`.

### 2. Забыт `SingletonImageLoader.setSafe`
Приложение работает, но без нашего кэша — дефолтный. На больших библиотеках обложки перегружаются при каждом скролле.

### 3. `MediaMetadataRetriever` не освобождён
Забыл `.release()` → утечка нативной памяти. Через несколько сотен треков OOM. Всегда `try/finally`.

### 4. `cacheDir` вместо `filesDir` для пользовательских данных
Система очистит `cacheDir` — пользователь увидит свои альбомы без обложек. Баг-отчёт: "обложки иногда исчезают".

### 5. `content://` URI из галереи сохранён в БД
URI, полученный из `PickVisualMedia`, становится недействительным после перезагрузки устройства. Поэтому сразу копируем байты в `filesDir` и храним путь к файлу.

### 6. Крупная картинка в оригинальном размере
Пользователь выбрал 12 MP-фото — оно 8 MB. Сохранил без сжатия — `filesDir` раздулся. Можно прогнать через `BitmapFactory.decodeByteArray` + `compress(JPEG, 85)` перед сохранением.

### 7. Обложка не обновляется после смены
Coil кэширует по ключу (= URI). Если по одному и тому же пути положил новый файл — Coil всё равно покажет старый. Решение: добавить версию в URI (`path?v=123`) или вручную `imageLoader.memoryCache?.remove(...)`.

### 8. Permissions для чтения из галереи
На Android 13+ `PickVisualMedia` сам решает вопрос — не требует `READ_MEDIA_IMAGES`. На старых — можно использовать или `PickVisualMedia` (он автоматически падает в `ACTION_GET_CONTENT`), или устаревший `ACTION_PICK`. У нас `PickVisualMedia` — универсальный, работает везде.

---

## Try yourself

1. **Проверь кэш**: включи airplane mode, перезапусти приложение. Обложки всё равно должны показаться — из диск-кэша.

2. **Посмотри директорию кэша**: в Android Studio Device Explorer → `/data/data/org.example.mp3player/cache/cover_art/`. Увидишь файлы с хешами.

3. **Сожми пользовательскую обложку**: добавь в `save()` сжатие до 80% JPEG и максимальной стороны 1024px. Используй `BitmapFactory.decodeByteArray` + `Bitmap.compress`.

4. **Плейсхолдер поинтереснее**: вместо иконки — градиент, основанный на hash от `track.title` (детерминированный, у одного трека всегда один цвет).

5. **Fallback через `CoverArtReader`**: если `coverUri` null (MediaStore не нашёл), попробуй `readEmbedded(track.path)`. Кэшируй результат — не читай дважды.

6. **Ripple при клике на обложку**: оборачивай `CoverImage` в `Modifier.clickable { ... }` там, где он в списке — клик уже есть, но ripple эффект будет обрезан. Добавь `Modifier.clip(shape)` до `clickable`.

---

## Финал

Если ты дошёл до сюда — у тебя работает полноценный плеер:

- [x] Сканирует локальные треки через MediaStore с обработкой permissions.
- [x] Автоматически группирует их в альбомы из метаданных.
- [x] Даёт создавать свои альбомы, хранит в Room, наблюдает через Flow.
- [x] Воспроизводит музыку через ExoPlayer + MediaSession.
- [x] Работает в фоне, имеет уведомление с кнопками, реагирует на Bluetooth.
- [x] Все зависимости идут через Koin.
- [x] Экраны следуют паттерну UiState + StateFlow.
- [x] Навигация через Navigation Compose с type-safe routes.
- [x] Интерфейс на русском и английском.
- [x] Красивые обложки с кэшем и плейсхолдерами.

**Что дальше (за пределами гайда):**

- **Эквалайзер** через `AudioEffect` + `Equalizer` + `BassBoost`.
- **Статистика прослушивания** — Room-таблица событий play/pause.
- **Last.fm scrobbling** через Ktor.
- **Виджет** для экрана блокировки (Glance).
- **Android Auto** — `MediaBrowserServiceCompat`-совместимость.
- **iOS-реализация** — заглушки `TODO` превращаются в нормальный код через MPMediaQuery, AVPlayer, CoreData.
- **Тесты** — начни с `UserAlbumsRepositoryImpl` и `TracksViewModel`.

Удачи 🎵

## Ссылки

- [Coil 3 — Jetpack Compose integration](https://coil-kt.github.io/coil/compose/)
- [Coil Multiplatform](https://coil-kt.github.io/coil/upgrading_to_coil3/)
- [MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever)
- [Photo picker (PickVisualMedia)](https://developer.android.com/training/data-storage/shared/photopicker)
