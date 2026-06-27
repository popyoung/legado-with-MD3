# Personal Patch Workflow

This fork follows upstream closely while keeping a small personal patch stack.
Use this workflow for every upstream main update or new formal release base.

## Fixed Rules

- `main` is pure upstream `main`. Do not commit personal changes to `main`.
- Release baselines are recorded as `upstream/<version>` branches, for example `upstream/3.27`.
- Personal integration branches are named `personal/<version>-tts`, for example `personal/3.27-tts`.
- Long-lived personal work against upstream main may use `personal/main`.
- Local Android toolchains are not used for this project. Do not run local JDK, SDK, NDK, Gradle, or `gradlew` checks unless explicitly requested.
- Verification and APK packaging use the single GitHub Actions workflow in `.github/workflows/ci.yml`.
- The workflow has one path only: compile, test, build `arm64-v8a` noR8 APK, upload artifact. Failures naturally stop later steps.

## Creating a New Release Base

When upstream publishes a release but there is no upstream tag in this repo, create an explicit baseline branch:

```bash
git fetch origin
git switch main
git pull --ff-only
git branch upstream/3.27 <commit-that-matches-the-release-source>
git push origin upstream/3.27
```

The `<commit-that-matches-the-release-source>` must be the source commit used by the release APK/source archive, not just the latest upstream main commit.

## Exporting Personal Patches

Keep personal changes as small, topic-focused commits. Export them from the current personal branch against its upstream baseline:

```bash
git format-patch --base=upstream/3.27 -o patches/personal/3.27 upstream/3.27..personal/3.27-tts
```

Recommended patch topics:

- GitHub Actions build workflow
- TTS visual following and highlight behavior
- TTS manual previous/next paragraph positioning
- Version, package, or release-specific build configuration

Avoid one large patch. Small patches make conflicts easier to isolate.

## Applying Patches to a New Release

Create the new personal branch from the release baseline, then apply the patch stack with three-way merge:

```bash
git switch -c personal/3.28-tts upstream/3.28
git am -3 patches/personal/3.27/*.patch
```

Use `-3` so Git can use three-way merge when upstream context changed.

## Conflict Handling

Enable recorded conflict resolution once:

```bash
git config rerere.enabled true
```

When `git am -3` stops on a conflict:

```bash
git status
# edit conflicted files
git add <fixed-files>
git am --continue
```

If a patch is obsolete for the new release:

```bash
git am --skip
```

If the application attempt should be discarded:

```bash
git am --abort
```

After resolving conflicts, inspect the resulting diff by feature area before pushing.

## Verification

Do not verify with local Gradle. Trigger the single GitHub Actions workflow:

```bash
gh workflow run ci.yml --ref personal/3.28-tts
```

The expected artifact is:

```text
legado-arm64-v8a-noR8-unsigned-apk
```

If the workflow fails, inspect the failing GitHub Actions logs and fix only the relevant patch.
