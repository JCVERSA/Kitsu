# FranimeApp

An Android application that serves as a specialized web-wrapper for the [Franime](https://franime.fr) site, designed to automatically monitor and extract direct video stream URLs.

## 🚀 Purpose

The primary goal of this application is to automate the process of capturing M3U8 or MP4 video links from the Franime streaming platform. It provides a native-friendly way to interface with the web content while maintaining a focus on security and maintainability.

## 🏗️ Architecture

The app follows a **Single Activity Architecture** using a WebView as the primary interface.

- **Native Layer (Kotlin)**: Manages the WebView lifecycle, handles security configurations, and provides a JavaScript bridge (`FranimeWebInterface`).
- **Extraction Layer (JavaScript)**: A modular script located in `app/src/main/assets/extractor.js` that is injected into the web page.
- **Inter-Process Communication**: Uses the `addJavascriptInterface` API to send discovered URLs from the browser context to the native Kotlin environment.

### Extraction Strategies

The injection script utilizes multiple fallback strategies to ensure high reliability:
1. **DOM Inspection**: Scans for `<video>` and `<source>` elements.
2. **Mutation Observation**: Watches for dynamically loaded video players using `MutationObserver`.
3. **Network Interception**: Hooks into `window.fetch` and `XMLHttpRequest` to catch manifest requests (HLS/DASH) in real-time.
4. **Polling**: Periodic fallback checks for highly dynamic content.

## 🔒 Security Features

- **Least Privilege**: WebView file and content access are disabled by default.
- **Isolated Execution**: The extraction logic is decoupled from the main Kotlin code.
- **Strict User-Agent**: Uses a modern mobile Chrome user-agent to ensure compatible web delivery.

## 🛠️ Tech Stack

- **Language**: Kotlin 2.0.0
- **UI Framework**: Android AppCompat (XML/Views)
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34
- **Build System**: Gradle Kotlin DSL (KTS) with Version Catalogs

## 📖 Build & Development

### Prerequisites
- Android Studio Koala+ or IntelliJ IDEA
- JDK 17

### Commands
- **Grant Permissions**: `chmod +x gradlew`
- **Build APK**: `./gradlew assembleDebug`
- **Run Tests**: `./gradlew test`
- **Project Sync**: `./gradlew help`

## 📝 Recent Changes
The project has recently been refactored to:
- Externalize the extraction script into assets.
- Improve Logcat observability for JavaScript console messages.
- Remove unused Jetpack Compose dependencies to reduce APK footprint.
- Align Gradle plugin versions for build stability.
