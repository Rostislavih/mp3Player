# Гайд по созданию Kotlin Multiplatform приложения для воспроизведения музыки

> **Пошаговый гайд по реализации:** если ты хочешь не просто "что это", а "как сделать" — иди в [`docs/guide/00-ROADMAP.md`](./docs/guide/00-ROADMAP.md). Там 9 файлов с инструкциями по каждой подсистеме (архитектура, сканирование, Room, ExoPlayer, Koin, ViewModel, навигация, обложки). Этот файл оставлен как общий обзор стека.

## Оглавление
1. [Введение](#введение)
2. [Структура проекта](#структура-проекта)
3. [Технологии и их назначение](#технологии-и-их-назначение)
4. [Основы Kotlin для понимания кода](#основы-kotlin-для-понимания-кода)
5. [Как строить и запускать](#как-строить-и-запускать)
6. [Рекомендации по изучению](#рекомендации-по-изучению)

---

## Введение

Этот проект — **Kotlin Multiplatform (KMP)** приложение для воспроизведения локальной музыки. Пишешь код один раз — работает на Android и iOS.

### Особенности твоего проекта
- **Compose Multiplatform** — единый UI для всех платформ
- **Koin** — внедрение зависимостей
- **Room** — локальная база данных
- **Coil** — загрузка изображений
- **Clean Architecture** — разделение на слои

---

## Структура проекта

```
mp3Player/
├── composeApp/              # Приложение (точка входа)
│   ├── src/androidMain/   # Android-специфичный код
│   ├── src/iosMain/       # iOS-специфичный код
│   └── src/commonMain/   # Общие ресурсы
├── shared/                 # Общий код для всех платформ
│   ├── data/              # Работа с данными (файлы, БД)
│   ├── domain/           # Бизнес-логика (модели, юзкейсы)
│   └── presentation/     # UI (экраны, ViewModel)
├── gradle/                # Gradle wrapper
└── settings.gradle.kts  # Настройки проекта
```

### Слои приложения (Clean Architecture)

| Слой | Где находится | Что делает |
|------|---------------|------------|
| **presentation** | `shared/presentation/` | Экраны, кнопки, ViewModel — UI |
| **domain** | `shared/domain/` | Модели треков, логика воспроизведения |
| **data** | `shared/data/` | Чтение файлов, доступ к БД |

---

## Технологии и их назначение

### 1. Jetpack Compose / Compose Multiplatform

**Что это:** Современный UI-фреймворк на Kotlin.

**Для чего нужен:**
- Создание интерфейса (кнопки, списки, экраны)
- Material Design 3 (красивый дизайн)
- Анимации

**В проекте используется:**
```kotlin
// Пример из проекта
@Composable
fun PlayerScreen() {
    Column {
        Text("Now Playing")
        Button(onClick = { /* play */ }) {
            Text("Play")
        }
    }
}
```

---

### 2. Kotlin Multiplatform (KMP)

**Что это:** Технология для написания кода, работающего на разных платформах.

**Для чего нужен:**
- Общий код между Android и iOS
- Один код — много платформ

**Как работает:**
```
commonMain/   → код для всех платформ
androidMain/ → только Android
iosMain/     → только iOS
```

---

### 3. Kotlin Coroutines + Flow

**Что это:** Инструменты для асинхронного программирования.

**Для чего нужен:**
- Асинхронные операции (чтение файлов)
- Потоковые данные (обновление плейлиста)
- Не блокировать UI

**Пример:**
```kotlin
// Flow — поток данных
val songs: Flow<List<Song>> = // получаем поток песен

// ViewModel собирает данные
viewModelScope.launch {
    songs.collect { list ->
        // обновляем UI
    }
}
```

**Ключевые понятия:**
- `suspend` — функция, которую можно приостановить
- `Flow` — поток, который emitит данные
- `launch` — запустить корутину

---

### 4. Koin (Dependency Injection)

**Что это:** Простой фреймворк для внедрения зависимостей.

**Для чего нужен:**
- Передача объектов в классы
- Управление зависимостями

**В проекте:**
```kotlin
val appModule = module {
    viewModel { PlayerViewModel(get()) }
    single { AudioPlayer() }
}
```

---

### 5. Coil

**Что это:** Библиотека для загрузки изображений.

**Для чего нужен:**
- Загрузка обложек альбомов
- Кэширование

**Пример:**
```kotlin
AsyncImage(
    model = "https://example.com/cover.jpg",
    contentDescription = "Album cover"
)
```

---

### 6. Room Database

**Что это:** Локальная база данных SQLite.

**Для чего нужен:**
- Сохранение избранного
- Кэширование списка песен
- История прослушивания

---

### Зависимости проекта (из libs.versions.toml)

```toml
[versions]
kotlin = "2.3.20"
composeMultiplatform = "1.10.3"
koin = "4.2.0-RC1"
room = "2.8.4"
coil = "3.4.0"
coroutines = "1.10.2"
material3 = "1.10.0-alpha05"
```

---

## Основы Kotlin для понимания кода

### 1. Переменные

```kotlin
// val — неизменяемая (рекомендуется)
val name: String = "Song Name"

// var — изменяемая
var isPlaying: Boolean = false
```

### 2. Функции

```kotlin
// Простая функция
fun playSong(song: Song) {
    println("Playing: ${song.title}")
}

// Функция с возвратом
fun getSongTitle(song: Song): String {
    return song.title
}
```

### 3. Классы (data class)

```kotlin
// data class — автоматически генерирует equals, hashCode, toString
data class Song(
    val title: String,
    val artist: String,
    val path: String,
    val duration: Long = 0
)
```

### 4. Nullable типы (могут быть null)

```kotlin
// ? означает может быть null
val song: Song? = null

// Безопасный вызов
song?.title

// Значение по умолчанию (Elvis operator)
val title = song?.title ?: "Unknown"
```

### 5. Lambda-функции (анонимные функции)

```kotlin
// Полная запись
val onClick: () -> Unit = { ->
    println("Clicked!")
}

// Сокращенная запись
Button(onClick = { println("Clicked!") })
```

### 6. Collections (коллекции)

```kotlin
// List — список
val songs: List<Song> = listOf(song1, song2, song3)

// Map — словарь (ключ-значение)
val songMap: Map<String, Song> = mapOf("1" to song1)

// Фильтрация
val filtered = songs.filter { it.artist == "Queen" }

// Map (преобразование)
val titles = songs.map { it.title }
```

### 7. Extension-функции

```kotlin
// Добавляем функцию к существующему классу
fun String.capitalizeWords(): String {
    return split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

// Использование
"hello world".capitalizeWords() // "Hello World"
```

### 8. Object и Companion Object

```kotlin
// Object — синглтон (один экземпляр)
object AudioPlayer {
    fun play() { }
}

// Companion Object — статические методы класса
class PlayerScreen {
    companion object {
        const val SCREEN_NAME = "Player"
    }
}
```

---

## Как строить и запускать

### Требования
- **Android Studio** (最新版)
- **JDK 17+**
- **Android SDK**

### Команды Gradle

```bash
# Собрать Android APK
./gradlew :composeApp:assembleDebug

# Запустить на устройстве
./gradlew :composeApp:installDebug

# Собрать iOS (только на Mac)
./gradlew :composeApp:linkDebugFrameworkIosArm64
```

### Запуск в Android Studio
1. Открой проект в Android Studio
2. Выбери `composeApp` в списке модулей
3. Нажми Run (зеленая кнопка)

---

## Рекомендации по изучению

### Порядок изучения (для новичка)

**Неделя 1-2: Основы Kotlin**
- Переменные, функции, классы
- Null safety
- Collections (List, Map)

**Неделя 3-4: Coroutines + Flow**
- suspend функции
- Запуск корутин
- Flow и collect

**Неделя 5-6: Compose**
- @Composable функции
- Состояние (remember, mutableStateOf)
- Lists (LazyColumn)

**Неделя 7-8: KMP**
- commonMain / androidMain / iosMain
- expect / actual

### Ресурсы

| Тема | Ссылка |
|------|--------|
| Kotlin Basics | https://kotlinlang.org/docs/home.html |
| Kotlin Coroutines | https://kotlinlang.org/docs/coroutines.html |
| Compose | https://www.jetbrains.com/compose/docs/ |
| KMP | https://kotlinlang.org/docs/multiplatform.html |

### Что делать

1. **Начни с малого** — измени текст кнопки
2. **Читай чужой код** — смотри как написаны Screen файлы
3. **Экспериментируй** — добавь новый экран
4. **Не копируй** — старайся понять как это работает

---

## FAQ

### Можно ли запустить на Windows?
Да, Android сборка работает на Windows.

### Можно ли запустить на iOS?
Нужен Mac с Xcode.

### Нужно ли знать Swift?
Нет, KMP генерирует нативный код сам.

### Где хранить MP3 файлы?
В `shared/data/` слое нужно реализовать сканирование директорий. Подробный пошаговый гайд — в [`docs/guide/02-PERMISSIONS_AND_SCAN.md`](./docs/guide/02-PERMISSIONS_AND_SCAN.md).

---

## Связанные документы

- [`docs/guide/00-ROADMAP.md`](./docs/guide/00-ROADMAP.md) — точка входа в пошаговый гайд, 9 этапов реализации
- [`docs/superpowers/specs/2026-04-23-music-player-guide-design.md`](./docs/superpowers/specs/2026-04-23-music-player-guide-design.md) — спецификация, по которой написан гайд

---

*Обновлено: 2026*