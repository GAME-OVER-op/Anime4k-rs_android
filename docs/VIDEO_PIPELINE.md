# Видео-пайплайн

Приложение повторяет старую схему `anime4k_video.sh`:

```text
video.mp4
↓ ffmpeg
PNG кадры
↓ Rust Anime4K-rs classic
upscaled PNG кадры
↓ ffmpeg
output.mp4 + оригинальное аудио
```

## Команды внутри приложения

Извлечение кадров:

```text
ffmpeg -i input.mp4 -vsync 0 frames/%08d.png
```

Обработка кадра:

```text
Anime4K-rs classic ядро: scale + iterations + pcs + pgs
```

Сборка видео:

```text
ffmpeg -framerate ORIGINAL_FPS -i upscaled/%08d.png -i input.mp4 \
  -map 0:v:0 -map 1:a? -c:v libx264, если доступен; иначе mpeg4 -crf Качество видео / CRF -preset PRESET \
  -pix_fmt yuv420p -c:a copy -shortest output.mp4
```

Если выбран режим FPS `simple`, добавляется `-r OUT_FPS`.

Если выбран `interpolate`, добавляется `-vf minterpolate=...`.
