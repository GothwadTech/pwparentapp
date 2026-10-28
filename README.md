# WebView App Builder🚀

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-FF6F00?style=for-the-badge)
![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)

**A reusable, offline-first Android WebView container built with Jetpack Compose, Material 3,
Room Database, and Firebase Cloud Messaging — rebrand it from one config block.**

[Template Usage](#-using-this-as-a-template) • [Features](#-key-features) • [Tech Stack](#-tech-stack) • [Getting Started](#-getting-started) • [CI/CD & Releases](#-cicd--signing-secrets)

</div>

---

## 📱 Overview

**A template for shipping a web app as a native Android app.** Point it at a URL and you get a
production-shaped container: native Compose chrome around a hardened WebView, offline caching
and a retryable offline screen, scoped camera/mic/geolocation, a Room database for local data,
and FCM push wired to a JavaScript bridge.

This checkout is branded as **Parent App** (`com.pw.parent`), built for the PW parent portal —
see **[Using this as a template](#-using-this-as-a-template)** for the config block that drives
the launcher label, application ID, JS bridge and notification channel.

---

## ✨ Key Features

- 🎨 **Light-only Material 3 chrome**: The hosted web app is light-only, so the container is too — pure white (`#FFFFFF`) status bar and navigation bar with dark system icons, no system-dynamic-color or dark-theme switching, no WebView auto-darkening.
- ⚡ **Offline-First Reliability**: Integrated Room Database along with WebView ServiceWorker caching ensuring fast load times and uninterrupted offline experience.
- 🔔 **Push Notifications**: Firebase Cloud Messaging (FCM) plumbing with a configurable notification channel and a JavaScript bridge the web app can call — activate it by adding `google-services.json` (see below).
- 🔄 **Modern State Management**: MVVM architecture utilizing Kotlin Coroutines, `StateFlow`, and `collectAsStateWithLifecycle`.
- 🧩 **Config-Driven Rebranding**: App name, application ID, version, JS bridge name, notification channel and target URL all come from `gradle.properties` / `.env`.
- 🔒 **Scoped Permissions**: camera, microphone and geolocation are granted only to the configured origin, requested lazily when the page needs them.
- 🛡️ **Automated CI/CD Workflows**: GitHub Actions build a signed Release APK and Play Store AAB on every push to `main` (and publish GitHub Releases on `v*` tags), with strict signing-secret validation. No debug or desktop packages are produced.

---

## 🛠 Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.x |
| **UI Framework** | Jetpack Compose (BOM), Material 3, Accompanist |
| **Architecture** | MVVM (Model-View-ViewModel) + Repository Pattern |
| **Local Storage** | Room Database + SQLite, Android Keystore |
| **Networking & API**| Retrofit, OkHttp 4, Moshi (Kotlin codegen) |
| **Push Notifications** | Firebase Cloud Messaging (FCM) |
| **Build System** | Gradle 9.3.1 (Kotlin DSL), Android Gradle Plugin (AGP) |
| **Testing** | Robolectric, Roborazzi, JUnit 4, AndroidX Test |

---

## 📂 Project Structure

```text
webview/                        # template root
├── .github/
│   └── workflows/
│       ├── build.yml          # Build & sign Release APK + AAB on push to main
│       └── release.yml        # Build & publish to GitHub Releases on tag (v*)
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/        # App assets & graphics
│   │   │   ├── java/com/pw/parent/
│   │   │   │   ├── data/      # Room Database, DAO, Repository
│   │   │   │   ├── ui/        # Compose Screens, ViewModels, Theme
│   │   │   │   └── utils/     # FCM Service, Notification Helpers
│   │   │   ├── res/           # Layouts, mipmaps, drawables, strings
│   │   │   └── AndroidManifest.xml
│   │   └── test/              # Local JVM and Robolectric unit tests
│   ├── build.gradle.kts       # App module configuration & dependencies
│   └── proguard-rules.pro     # ProGuard / R8 rules
├── gradle/
│   ├── libs.versions.toml     # Version catalog
│   └── wrapper/               # Gradle wrapper executable & properties
├── gradle.properties          # ⭐ Template configuration (app.name, app.id, JS bridge, ...)
├── .env.example               # ⭐ TARGET_URL for the web app (copy to .env)
├── build.gradle.kts           # Root build configuration
├── settings.gradle.kts        # Project settings & plugin resolution
└── README.md                  # Documentation
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio**: Ladybug (2024.2.1+) or newer recommended.
- **JDK**: Java 17 or Java 21 (Temurin / Eclipse Adoptium recommended).
- **Android SDK**: API Level 35 (compileSdk & targetSdk), Minimum API Level 23.

### Local Installation & Build

1. **Clone the repository:**
   ```bash
   git clone https://github.com/GothwadTech/pwparentapp.git
   cd pwparentapp
   ```

2. **Setup environment variables:**
   ```bash
   cp .env.example .env
   ```

3. **Build the Debug APK:**
   ```bash
   chmod +x ./gradlew
   ./gradlew assembleDebug
   ```
   The debug APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

4. **Run Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 🧩 Using this as a template

This repository is a **reusable WebView container**. The values currently shipped
(`Parent App`, `com.pw.parent`) are read by the build, so rebranding means editing the config
block below plus the launcher artwork — no code changes required.

### 1. The config block (`gradle.properties`)

| Key | What it controls |
| :--- | :--- |
| `app.name` | Launcher label, notification fallback title, JS bridge fallback token |
| `app.id` | `applicationId` (Play Store / package identity) |
| `app.versionCode` / `app.versionName` | Local build version — CI overrides these with `-PversionCode` / `-PversionName` and auto-increments per release |
| `app.jsBridgeName` | The injected bridge name; the web app calls `window.<name>.postNotification(...)`, `.getPushToken()`, `.isDeviceOnline()`, `.saveOfflineDraft()`, `.showToast()`, `.setTheme()` |
| `app.notificationChannelId` / `Name` / `Description` | Android notification channel |
| `app.prefsName` | SharedPreferences file that caches the FCM token |

### 2. The web target (`.env`)

```bash
cp .env.example .env     # then set TARGET_URL to your web app
```

`TARGET_URL` is the **only** allowed origin: camera, microphone and geolocation requests from
any other origin are denied, and it drives the WebView's initial load. Change it and the
session/theme/caching behaviour follows automatically.

### 3. Rebrand checklist for a new app

1. Edit the `app.*` block in `gradle.properties`.
2. Set `TARGET_URL` in `.env`.
3. Replace the launcher artwork: drop the exported icon set in `icon.zip` (repo root) and run
   `python3 tools/make_icons.py`. It regenerates `mipmap-*/ic_launcher*` (legacy + adaptive
   foreground/monochrome), the density-aware splash logo and `assets/icon_512.png`.
4. *(Optional)* Rename the Kotlin package/classes (`com.pw.parent.*`, `Parent*` classes,
   the `parent_app_database` Room file). These are **not** part of the config block — they are
   internal identifiers, so they only matter if you want the source tree to look neutral.
5. Add `google-services.json` + the `google-services` plugin if the app needs push (see below).

> **`app.id` vs package name:** changing `applicationId` is enough for a new store listing —
> keep the Kotlin `namespace` in sync with `app.id` to avoid drift. In this repo both are
> `com.pw.parent`, and the sources live under `app/src/*/java/com/pw/parent/`.

### 4. What the template already handles

- Offline-first: `LOAD_CACHE_ELSE_NETWORK` + ServiceWorker cache when the device is offline, a
  retryable offline panel over a still-mounted WebView (page state survives a network blip).
- Permission scoping: camera/mic/geolocation only for `TARGET_URL`'s origin, asked lazily when
  the page needs them; no permission spam on first launch.
- Light-only rendering: force-dark and Android 13+ algorithmic darkening are switched off, the
  WebView background is white and the page's `color-scheme` is pinned to `light`.
- **Launch splash**: `androidx.core:splash-screen` keeps the branded splash (white + app icon)
  on screen until the WebView reports its first committed frame (`onPageCommitVisible`), with a
  12-second hard timeout as a safety net — so there is never a blank window between splash and site.
- Loading feedback is a 2dp progress bar at the top instead of a full-screen scrim, so the page
  is never covered while it loads.
- Room database for offline drafts + a notification log.
- FCM service + notification channel + `ParentApp`-style JS bridge.

---

## ⏰ Scheduled Parent Reminders (works WITHOUT Firebase)

The app can nudge parents on its own — **no FCM, no backend, no `google-services.json` needed**.

- **When:** every **Monday**, at **06:00, 08:00, 10:00, 12:00, 14:00, 16:00 and 18:00** (local time)
  — i.e. a reminder every 2 hours from morning to evening, the window weekly results are shared.
- **What:** a rotating set of parent-facing messages ("Parents, please track your child's
  performance", "Test result is out", "Weekly progress update", …) defined in
  `utils/ReminderScheduler.kt` (`MESSAGES`).
- **How:** `ReminderScheduler` keeps exactly one chained `OneTimeWorkRequest` alive. The worker
  posts the notification and queues the next slot, so there is no polling and no battery drain.
  WorkManager persists the chain across reboots, app kills and Doze; the schedule is recomputed on
  every app start so timezone/clock changes cannot drift it.
- **Duplicates / late runs:** each slot carries its timestamp, a slot already delivered is skipped
  (duplicate executions are harmless) and a slot that is more than 3 hours late is dropped instead
  of spamming the parent.
- **Control from the web app:** `window.<app.jsBridgeName>.setRemindersEnabled(false)` /
  `.getRemindersEnabled()`. Default is **enabled**.
- **Requires** the `POST_NOTIFICATIONS` runtime permission (already requested on first launch);
  without it the reminders are silently skipped by Android.

### Testing a reminder without waiting for Monday

The real reminders only fire on Monday, so there are two instant ways to check the notification:

1. **Hidden gesture (on device, works in release builds):** swipe **left↔right twice** in the strip
   immediately **below** the status bar (the 48dp band at the very top of the page — the status bar
   itself is SystemUI and no app can read touches there). The first swipe shows a small toast, the
   second posts one notification. Repeating the pair is rate-limited (8s cooldown) so it cannot spam.
2. **From the web app:** `window.<app.jsBridgeName>.sendTestReminder();` posts the same
   notification immediately and returns the title used.

Pure-JVM tests for the cadence live in `app/src/test/java/com/pw/parent/ReminderScheduleTest.kt`
and for the gesture in `ReminderSwipeTrackerTest.kt`. Run them locally with:

```bash
./gradlew testReleaseUnitTest --tests "com.pw.parent.Reminder*"
```

They are not wired into the CI job yet: with AGP 9 + Gradle 9 + KSP, the unit-test variant's KSP
task crashes in CI (`ApplicationManager.getApplication() is null`) while `assembleRelease` builds
fine. `tools/report_test_failures.py` is ready to surface their failures as check annotations once
that is fixed.

---

## 🔔 Push Notifications (FCM) — setup required

The app ships with FCM code, but **push notifications stay disabled until Firebase is wired up**.
Both of these are required:

1. Add your `google-services.json` to the `app/` directory.
2. Apply the Google Services plugin — uncomment in `app/build.gradle.kts`:
   ```kotlin
   plugins {
     // ...
     alias(libs.plugins.google.services)
   }
   ```

Without them, `FirebaseApp.getApps()` is empty, the app logs
`Firebase is not configured ... Push notifications are DISABLED.`, and
`window.ParentApp.getPushToken()` returns a locally generated placeholder token.
(Previously the app silently initialised Firebase with a fake API key, so token
retrieval failed forever while every piece of the notification stack *looked* connected.)

---

## 🔐 CI/CD & Signing Secrets

The repository includes pre-configured GitHub Actions workflows for continuous integration and automated release deployments.

### Required GitHub Secrets

To build and sign Release APKs & Play Store AAB bundles automatically, add the following secrets to your GitHub repository under **Settings > Secrets and variables > Actions**:

| Secret Name | Description | Required |
| :--- | :--- | :---: |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release `.jks` or `.keystore` file | **Yes** |
| `KEYSTORE_PASSWORD` | Password for your release keystore | **Yes** |
| `KEY_ALIAS` | Key alias name inside the keystore | Optional |
| `KEY_PASSWORD` | Password for the key alias | Optional |

> **Note**: For security, if `RELEASE_KEYSTORE_BASE64` or `KEYSTORE_PASSWORD` is not configured, the release build step will automatically abort to prevent deploying unverified or improperly signed builds.

### Generating `RELEASE_KEYSTORE_BASE64`

You can convert your local `.jks` or `.keystore` file into Base64 using:

**Linux / macOS:**
```bash
base64 -i my-release-key.jks | tr -d '\n' > keystore_base64.txt
```

**Windows (PowerShell):**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-release-key.jks")) | Set-Content keystore_base64.txt
```
Copy the contents of `keystore_base64.txt` and paste it into GitHub Secrets as `RELEASE_KEYSTORE_BASE64`.

---

## 🏷️ Triggering a Release

To trigger an official GitHub Release:

1. Create a version tag locally:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. The `Release to GitHub Releases` workflow will automatically:
   - Validate signing secrets.
   - Self-heal Gradle wrapper if needed.
   - Build the signed Release APK and Play Store AAB only.
   - Publish a new GitHub Release (`Parent App v<version>`) with generated release notes and
     two downloadable assets: `Parent-App-v<version>-Release.apk` and
     `Parent-App-v<version>-PlayStore.aab`.

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the Apache License 2.0. See `LICENSE` for more information.
