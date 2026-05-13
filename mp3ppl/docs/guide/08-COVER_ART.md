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

`gradle/libs.versions.toml`:

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

**Почему `filesDir`, а не `cacheDir`?** `cacheDir` Android может чистить в любой момент, когда ему нужно место. `filesDir` — только при удалении приложения. Для пользовательских данных (они их не хотят терять) — `filesDir`.

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

---

## Разбор

### Что такое `ImageLoader` и почему он один на приложение

```kotlin
fun createImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .crossfade(true)
    .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
    .diskCache { DiskCache.Builder().directory(...).maxSizeBytes(50L * 1024 * 1024).build() }
    .build()
```

`ImageLoader` — это **главный объект Coil**. В нём живут:

- Memory-cache (LRU в RAM).
- Disk-cache (LRU на диске).
- Executor для загрузки (по умолчанию — пул потоков).
- Сетевые транспорты (HTTP-клиент, для `https://`-URL'ов).
- Декодеры форматов (PNG, JPEG, GIF, WebP, SVG если включить расширение).
- Logger, ComponentRegistry и т.п.

Один `ImageLoader` обслуживает все `AsyncImage` в приложении. Если бы их было два — каждый держал бы свой кэш, и одни и те же обложки лежали бы в памяти/на диске дважды. Поэтому **один синглтон на процесс**.

### `MemoryCache.maxSizePercent(context, 0.25)` — конкретные числа

```kotlin
MemoryCache.Builder().maxSizePercent(context, 0.25).build()
```

`maxSizePercent(context, 0.25)` означает: «возьми 25% от `Runtime.getRuntime().maxMemory()`, выраженную в байтах, и не превышай этот лимит».

Численный пример. На обычном Android-устройстве `maxMemory` для приложения — ~256 MB (точное значение зависит от устройства; есть устройства с 192, есть с 512). 25% от 256 MB = **64 MB**.

64 MB — это сотни обложек небольшого размера. Coil сам ужимает обложки до размера `AsyncImage` (если в UI 56dp обложка, в кэш ляжет картинка 56dp, не оригинал в 1024x1024). Поэтому памяти хватает с большим запасом.

При нехватке Coil выкидывает наименее используемые (LRU): «не показывали этот item уже долго → удалим его кэш-запись».

### `okio.Path` и `toOkioPath()` — зачем

```kotlin
.directory(context.cacheDir.resolve("cover_art").toOkioPath())
```

`context.cacheDir` — это `java.io.File`. Coil3 не использует `java.io.File` напрямую, он использует **`okio.Path`** — абстракция файлового пути из библиотеки Okio (Square).

Почему: Coil3 — KMP-библиотека. На iOS нет `java.io.File`. Okio даёт единый Path-API, который под Android маппится в `java.io.File`, под iOS — в native NSURL/NSPath, под JS — в виртуальную файловую систему и т.п. Один и тот же код Coil работает везде.

`File.toOkioPath()` — extension из okio, конвертирующий `java.io.File` → `okio.Path`. Это просто обёртка вокруг абсолютного пути.

В наших Android-сборках это не имеет практического значения (всё работает как обычный `File`), но если завтра захочется собрать iOS-таргет — никаких изменений в этом коде не потребуется.

### `crossfade(true)` — что физически делает

`AsyncImage` под капотом — это `Box` с двумя слоями: текущая картинка + индикатор загрузки/ошибки. Состояние загрузки представлено через `AsyncImagePainter.State`:
- `Empty` — ничего ещё не запросили.
- `Loading` — идёт загрузка.
- `Success` — картинка готова.
- `Error` — не получилось.

С `crossfade(true)` при переходе из `Loading` в `Success` Coil использует **`CrossfadeTransition`**: анимирует `alpha` нового изображения от 0 до 1 за ~100 мс. Старая картинка (или плейсхолдер) одновременно уходит в 0.

Без `crossfade` обновление мгновенное — это «дёргано»: только что был чистый фон, через 16 мс появилась обложка резко. С `crossfade` — плавный fade-in, выглядит профессионально.

`crossfade(durationMillis = 200)` — настройка длительности. По умолчанию — 100 мс.

### `SingletonImageLoader.setSafe { createImageLoader(this) }` — лямбда-провайдер

```kotlin
SingletonImageLoader.setSafe { createImageLoader(this) }
```

Сигнатура `setSafe`:

```kotlin
fun setSafe(factory: SingletonImageLoader.Factory)

fun interface Factory {
    fun newImageLoader(context: PlatformContext): ImageLoader
}
```

`setSafe { ... }` принимает **лямбду-фабрику**. Она НЕ вызывается сразу. Coil сохраняет лямбду внутри и вызывает её **при первом обращении** к глобальному ImageLoader (например, при первом `AsyncImage`).

Зачем lazy-инициализация:
- ImageLoader создаёт пулы потоков, открывает disk cache, читает индекс — это занимает несколько мс.
- Если пользователь не открывает экраны с картинками сразу — мы экономим эти мс на старте.

`setSafe` vs `set`:
- `set(factory)` — упадёт с `IllegalStateException`, если фабрика уже была установлена.
- `setSafe(factory)` — тихо игнорирует повторный вызов.

Зачем `setSafe`: при `Application.onCreate` он вызывается один раз. Но если используешь Coil и в инструментальных тестах — там Application пересоздаётся, и `set` мог бы упасть. `setSafe` спасает.

### `MediaMetadataRetriever` — нативный объект

```kotlin
val retriever = MediaMetadataRetriever()
try {
    retriever.setDataSource(...)
    retriever.embeddedPicture
} finally {
    retriever.release()
}
```

`MediaMetadataRetriever` — это Java-обёртка над **native C++ объектом**, который умеет парсить медиа-контейнеры (MP4, MP3, FLAC и т.д.). Native-часть держит:
- file descriptor (открытый файл),
- буферы парсинга,
- декодеры конкретного формата.

Это всё **не управляется GC**. Если ты потерял ссылку на `MediaMetadataRetriever` без `release()` — нативные ресурсы остаются. Через несколько сотен таких утечек — ANR, OOM, или система перестанет давать новые file descriptor.

`try/finally` — обязателен. Использование `.use { }` тоже работает, потому что `MediaMetadataRetriever` реализует `AutoCloseable` начиная с API 29.

`embeddedPicture` — свойство, которое читает байты обложки из метаданных. Возвращает `ByteArray?` (может быть null, если обложки нет). Внутри — парсинг ID3-тега для MP3, MP4 atoms для M4A, Vorbis comment для FLAC.

Альтернатива — `MediaStore.Audio.Media.AlbumArt` (то, что мы используем как основной источник). Она быстрее, потому что MediaStore уже распарсил все треки при индексации. `MediaMetadataRetriever` мы используем как **fallback**, когда MediaStore по какой-то причине не нашёл обложку.

### `bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }`

Эта компактная строка содержит несколько идиом:

1. **`openInputStream(uri)`** — возвращает `InputStream?`. Может быть `null`, если URI невалидный или нет прав.
2. **`?.use { ... }`** — safe call + `Closeable.use`. Если stream не null — открой блок, после блока (или при исключении) автоматически закрой.
3. **`it.readBytes()`** — extension Kotlin, читает stream до конца, возвращает `ByteArray`.
4. Результат всего выражения: `ByteArray?` (null если openInputStream вернул null, иначе байты).

Без `use` пришлось бы:

```kotlin
val stream = context.contentResolver.openInputStream(uri)
val bytes = try {
    stream?.readBytes()
} finally {
    stream?.close()
}
```

С `use` — одна строка, гарантированное закрытие. Эта же идиома используется для `Cursor` в файле 02 (см. там подробный разбор `?.use`).

### Coil cache key — как Coil решает «грузить или взять из кэша»

Coil идентифицирует загрузку по **ключу**, который выводится из `model` (то, что ты передал в `AsyncImage`). Для:
- `String` URL — ключ = строка URL.
- `Uri` — ключ = `uri.toString()`.
- `File` — ключ = абсолютный путь.

Два разных `AsyncImage` с одинаковым `model` → один и тот же ключ → одна загрузка в фоне, оба компонента получат результат. Это бесплатная дедупликация.

**Подвох:** если ты заменил содержимое файла (например, пользователь сменил обложку альбома и мы перезаписали `42.jpg`), но путь остался прежним — Coil **не узнает** об изменении. Он покажет закэшированную старую версию.

Решения:
1. **Менять URI**: `path?v=1` → `path?v=2` после смены. Coil увидит новый ключ → загрузит заново.
2. **Ручная инвалидация**: `imageLoader.memoryCache?.remove(MemoryCache.Key(oldKey))` + `imageLoader.diskCache?.remove(oldKey)`.
3. **Передача ImageRequest с `memoryCachePolicy(WRITE_ONLY)`** — кэш писать, но не читать (всегда грузим заново).

Для нашего MVP подойдёт вариант 1 — добавлять версию в URI при смене обложки.

### `PickVisualMedia` — что под капотом

```kotlin
val pickImage = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
) { uri -> ... }

pickImage.launch(PickVisualMediaRequest(...))
```

`PickVisualMedia` — это «системный photo picker», добавлен в Android 13 (API 33) как часть privacy-улучшений. Под капотом:

- **Android 13+ (API 33+):** запускается отдельная Activity Google Photo Picker, которая показывает галерею **без необходимости разрешения** `READ_MEDIA_IMAGES`. Пользователь сам выбирает, что хочет дать.
- **Android 11-12 (API 30-32):** через Google Play Services шлёпает аналогичный picker.
- **Android 10 и ниже:** автоматический fallback на `ACTION_GET_CONTENT` (старый метод выбора файлов через intent).

Преимущество для нас — единое API, работающее на всех версиях, и **никаких permission'ов** просить не надо. Пользователь даёт доступ к выбранному файлу через temporary URI grant.

`PickVisualMediaRequest` принимает фильтр: `ImageOnly`, `VideoOnly`, `ImageAndVideo`. Возвращает `Uri?` через callback: `null` если пользователь отменил, иначе URI выбранного файла.

URI имеет временный grant — он работает, пока процесс жив. Поэтому мы **сразу копируем байты** в свой `filesDir`: после перезагрузки устройства / kill процесса URI станет невалидным.

### `MaterialTheme.colorScheme.surfaceVariant`

```kotlin
.background(MaterialTheme.colorScheme.surfaceVariant)
```

`MaterialTheme` — Compose-обёртка над дизайн-системой Material 3. У неё есть три ключевых свойства:
- `colorScheme: ColorScheme` — палитра цветов (~30 named colors).
- `typography: Typography` — стили шрифтов (`titleLarge`, `bodyMedium`, ...).
- `shapes: Shapes` — формы для скруглений.

`colorScheme.surfaceVariant` — один из «фоновых» цветов, чуть отличающийся от `surface`. Material рекомендует использовать его для «приподнятых» поверхностей (карточки, плейсхолдеры). Цвет адаптируется к light/dark теме автоматически.

Чтобы переопределить — обернуть `setContent` в `MaterialTheme(colorScheme = darkColorScheme())` или собрать кастомную палитру.

`MaterialTheme.colorScheme.*` доступен только внутри `@Composable`-функций (это `CompositionLocal`). Поэтому ты не можешь сохранить цвет в обычной константе — только взять прямо в Composable.

### Coil3 vs Coil2 — пакеты

Coil3 — полностью переписан под KMP. Coil2 был Android-only. Главное различие — пакеты:

| Coil2 | Coil3 |
|---|---|
| `io.coil-kt:coil-compose` | `io.coil-kt.coil3:coil-compose` |
| `import coil.compose.AsyncImage` | `import coil3.compose.AsyncImage` |
| `coil.ImageLoader` | `coil3.ImageLoader` |

Будь осторожен с туториалами — если видишь `import coil.compose.AsyncImage` — это Coil2, не подойдёт.

### Почему обложки треков из MediaStore, а пользовательских — в `filesDir`?

Треки физически лежат на устройстве, MediaStore сам индексирует и кэширует их обложки. Когда трек удаляется — MediaStore обновляется автоматически.

Пользовательские обложки — наши данные. Система их не знает. Должны сами хранить и чистить. Используем `filesDir` (а не `cacheDir`), потому что `cacheDir` система может очистить в любой момент при нехватке места — пользователь увидит свои альбомы без обложек, баг.

### `contentScale = ContentScale.Crop`

Картинка разного соотношения сторон не искажается — кропается до квадрата. Для обложек — стандарт.

---

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
