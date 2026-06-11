# Anime4K Апскейлер Android

Android-приложение с классическим Rust-ядром Anime4K-rs и видео-пайплайном по варианту A: **FFmpeg → PNG-кадры → Rust Anime4K-rs → MP4**.

Интерфейс на русском. Настройки оставлены только те, которые реально используются классическим Anime4K-rs и старым `anime4k_video.sh`.

## Что реализовано

- Фото-режим: выбрать изображение → улучшить через Rust → сохранить/поделиться.
- Видео-режим: выбрать видео → разобрать на PNG → параллельно улучшить кадры через Rust → собрать MP4 через встроенный FFmpeg.
- Сохранение результата в галерею через MediaStore.
- Поделиться результатом через Android Share Intent + FileProvider.
- Прогресс обработки.
- Параллельный upscale кадров для мощных устройств.
- GitHub Actions workflow для сборки APK.

## Реальные настройки

### Качество кадра

- **Масштаб** — аналог `anime4k -s`.
- **Количество прогонов** — аналог `anime4k -i`.
- **Push Gradient** — аналог `--pgs`.
- **Push Color** — аналог `--pcs`.

### Видео

- **Одновременных upscale-процессов** — сколько кадров обрабатывать одновременно.
- **Выходной FPS** — `0` значит оставить оригинальный FPS.
- **Режим FPS**:
  - `keep` — оставить оригинальный FPS;
  - `simple` — использовать `ffmpeg -r`;
  - `interpolate` — использовать `ffmpeg minterpolate`.
- **Качество видео / CRF** — качество libx264, если доступен; иначе mpeg4, меньше = лучше/больше файл.
- **Preset** — скорость кодирования ffmpeg.
- **Удалять исходные PNG после upscale**.
- **Оставить рабочую папку**.
- **Пауза каждые N кадров**.
- **Длительность паузы**.

У числовых параметров есть ползунок и ручной ввод. Если нужно значение больше диапазона ползунка — введи число вручную.

## Сборка на GitHub

1. Залей проект в GitHub.
2. Открой **Actions**.
3. Запусти **Build Android APK**.
4. Скачай APK из Artifacts.

## Локальная сборка

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
gradle assembleRelease
```

APK:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

## Важно

Видео-обработка создаёт много временных PNG-файлов. Для длинных видео нужно много свободного места. Если включить “Удалять исходные PNG после upscale”, место расходуется меньше.


## FFmpeg внутри APK

FFmpegKit больше не используется. Приложение запускает встроенные Android `ffmpeg` и `ffprobe` executables через `ProcessBuilder`, ближе к старому Termux-скрипту.

GitHub Actions скачивает arm64-v8a бинарники перед сборкой и кладёт их в:

```text
app/src/main/jniLibs/arm64-v8a/libffmpeg.so
app/src/main/jniLibs/arm64-v8a/libffprobe.so
```

Если в бинарнике есть `libx264`, приложение использует `-c:v libx264 -crf`. Если `libx264` нет, автоматически используется совместимый fallback `-c:v mpeg4 -q:v`.
