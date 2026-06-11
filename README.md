# Anime4K Апскейлер Android

Новое Android-приложение с Rust-ядром Anime4K-rs. Интерфейс на русском, настройки вынесены в понятные переключатели и ползунки.

## Что уже есть

- Android-приложение на Kotlin + Jetpack Compose.
- Rust-ядро как JNI-библиотека.
- Русский экран настроек с ползунками и ручным вводом чисел.
- Обработка изображений через Rust.
- GitHub Actions workflow для сборки APK.
- Задел под будущую обработку видео: параллельные процессы, FPS, CRF, preset, паузы, удаление кадров.

> Текущий MVP обрабатывает изображения. Видео-пайплайн следующим шагом лучше добавить как очередь: декодирование видео → кадры → Rust batch → сборка mp4.

## Настройки в приложении

- **Режим**: `legacy`, `a`, `b`, `c`, `aa`, `bb`, `ca`.
- **Масштаб**: x1–x4.
- **Качество**: `fast`, `balanced`, `high`, `ultra`.
- **Итерации**: сила повторного восстановления линий.
- **Push Gradient**: восстановление контуров.
- **Push Color**: подтягивание цветовых областей.
- **Шумоподавление**: `off`, `low`, `medium`, `high`.
- **Deblur**: повышение резкости.
- **Затемнение линий**: визуальный эффект 4K-ремастера.
- **Утончение линий**: аккуратная коррекция толщины контуров.
- **Защита светлых областей**: уменьшение ringing/overshoot.

## Локальная сборка

Нужно установить:

- Android Studio / Android SDK
- Android NDK
- Rust
- cargo-ndk

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
gradle assembleRelease
```

APK будет тут:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

## Сборка на GitHub

1. Создай репозиторий на GitHub.
2. Залей проект.
3. Открой вкладку **Actions**.
4. Запусти workflow **Build Android APK**.
5. Забери APK из **Artifacts**.

Workflow лежит здесь:

```text
.github/workflows/android.yml
```

## Структура

```text
app/                      Android UI
rust/                     Rust JNI core
.github/workflows/        GitHub APK build
docs/                     документация
```

## Следующие этапы

- Добавить видео-экран.
- Добавить очередь задач и прогресс обработки.
- Добавить FFmpegKit или MediaCodec pipeline.
- Добавить сохранение результата в галерею.
- Добавить GPU backend для оригинальных Anime4K GLSL/CNN-шейдеров.
