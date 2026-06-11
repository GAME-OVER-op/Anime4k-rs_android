# Сборка APK на GitHub

## Быстрый вариант

```bash
git init
git add .
git commit -m "Initial Anime4K Android app"
git branch -M main
git remote add origin https://github.com/USER/REPO.git
git push -u origin main
```

После push открой GitHub → Actions → Build Android APK → Run workflow.

## Где APK

После завершения workflow скачай artifact:

```text
Anime4K-Upscaler-release-apk
```

Внутри будет release APK.

## Что делает workflow

- ставит JDK 17;
- ставит Android SDK/NDK;
- ставит Rust targets для Android;
- ставит `cargo-ndk`;
- собирает Rust JNI `.so`;
- собирает Android APK;
- загружает APK как artifact.
