# Agent Instructions

## Personal Fork Maintenance

- Read `docs/personal-patch-workflow.md` before changing branch, patch, release, TTS, or build workflow behavior.
- `main` is pure upstream main. Do not commit personal changes to `main`.
- Use `upstream/<version>` branches, such as `upstream/3.27`, to record formal release baselines when upstream tags are unavailable.
- Keep personal changes on `personal/<version>-tts` or `personal/main`.
- Do not run local JDK, SDK, NDK, Gradle, or `gradlew` commands for this project unless the user explicitly asks.
- Verification and APK packaging must use the single GitHub Actions workflow `.github/workflows/ci.yml`.
