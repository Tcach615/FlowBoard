---
name: build-apk
description: Build an installable Android debug APK for this repository in GitHub Actions and download it. Use when asked to build, produce, verify, or inspect an APK.
---

# Build an APK

Builds run on GitHub-hosted runners, never locally. You do not need Java, the Android
SDK, or Gradle installed to trigger one.

The workflow is `.github/workflows/build-apk.yml`. It pins JDK 17, Android
`platforms;android-35`, `build-tools;35.0.0` and command-line tools build `11076708`,
accepts SDK licenses, caches Gradle + the SDK, builds `:app:assembleDebug`, and fails the
job if no APK appears.

## When to use

- You changed Android code and need an installable artifact.
- You need to confirm the project still compiles for Android.
- Someone asks for "an APK" or "a build" without specifying release signing.

Do **not** use it for release publishing: signed release builds only run when the
repository already has the keystore secrets (see below).

## Commands

```bash
# 1. Trigger (branch must contain this workflow file; main is required for push builds)
gh workflow run build-apk.yml --ref main

# 2. Watch the run to completion; --exit-status makes a failure a non-zero exit
gh run list --workflow=build-apk.yml --limit 1
gh run watch "$(gh run list --workflow=build-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId')" --exit-status

# 3. Download the artifact into ./dist
gh run download "$(gh run list --workflow=build-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId')" -D dist
```

Overrides for multi-module or nested projects:

```bash
gh workflow run build-apk.yml --ref main -f module=app -f project_dir=android
```

Non-default branch: `gh workflow run build-apk.yml --ref <branch>`.

## Where the APK lands

- Inside the run: `app/build/outputs/apk/debug/app-debug.apk` (Flutter:
  `build/app/outputs/flutter-apk/app-debug.apk`), copied to `artifacts/` and attached to
  the run as `debug-apk-<run_number>`.
- Downloaded: `dist/debug-apk-<run_number>/app-debug.apk`.
- Install with `adb install -r dist/debug-apk-*/app-debug.apk`.

The workflow also writes package name, version and sha256 to the run's job summary.

## Release builds

Optional and gated. Provide all four repository secrets and the workflow adds a signed
`assembleRelease` step and its APK:

`ANDROID_KEYSTORE_BASE64` (base64 of the .jks), `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

The keystore is materialized only in `$RUNNER_TEMP`, passed via Gradle injected-signing
properties, and deleted afterwards. Never commit a keystore, print a secret, or paste one
into chat.

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `Failed to install the following SDK components ... licenses not accepted` | Run `yes \| sdkmanager --licenses` before installing. In this repo that is already in the "Install pinned Android SDK" step; if you added SDK packages, add them there too. |
| `Unsupported class file major version` / `The supplied javaHome seems to be invalid` / `Android Gradle plugin requires Java 17` | JDK mismatch. Change `JAVA_VERSION` **and** `actions/setup-java`'s `java-version` together, and the `.devcontainer/Dockerfile` base image, to the JDK the Gradle wrapper/AGP needs (AGP 8.x → 17). |
| `./gradlew: Permission denied` / `No executable gradlew` | Wrapper bit lost in git. The workflow runs `chmod +x ./gradlew`; commit `gradle/wrapper/gradle-wrapper.jar` and `gradlew` with mode 755 (`git update-index --chmod=+x gradlew`). |
| `Java heap space`, `GC overhead limit exceeded`, `Gradle build daemon disappeared` | Out of memory. `GRADLE_OPTS` sets `-Xmx4g`; lower module count, disable `org.gradle.parallel`, or drop to `-Xmx3g`. |
| `Project 'app' not found in root project` / `Cannot locate tasks that match ':app:assembleDebug'` | Wrong module. List modules with `./gradlew projects`, then rerun with `-f module=<name>`. |
| `No Android project found` | Auto-detection found no `gradlew`, Flutter `pubspec.yaml` or Expo `package.json`. Set `project_dir`, or add the Android module. |
| `No APK was found under */build/outputs/apk/**` | The build "succeeded" but produced nothing: check the module's `applicationId`/variant wiring and that the module is an `com.android.application` (not a library). |
| `Could not resolve ...` / downloads crawl | Missing cache hit or offline network. The Gradle cache is keyed on `**/*.gradle*`; verify `gradle/wrapper/gradle-wrapper.properties` is committed. |

## Local builds (optional)

A reproducible container is in `.devcontainer/` (same JDK/SDK pins as CI). With Docker:

```bash
docker build -t flowboard-android .devcontainer
docker run --rm -v "$PWD:/workspace" -w /workspace flowboard-android ./gradlew :app:assembleDebug
```

Or open the repo in a devcontainer / Codespace and run `./gradlew :app:assembleDebug`.
Output is `app/build/outputs/apk/debug/app-debug.apk`.

## Repository state check

This repository currently has no Android module (only `README.md`), so the workflow stops
at the "Detect stack" step with `No Android project found`. That is expected, not a broken
workflow. Confirm with `git ls-files | grep -E 'gradlew|build.gradle|pubspec.yaml'`.
