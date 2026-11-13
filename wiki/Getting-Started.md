# 🚀 Getting Started with Locara

This guide walks you through integrating **Locara v1.15.1** into your Katalon Studio project or standard Appium test suite in under 3 minutes.

---

## 1. Prerequisites

- **Java Development Kit (JDK)**: Version 17, 21, or 24 LTS.
- **Katalon Studio Enterprise**: 8.x – 11.7.0+ (Mobile / Web Automation License), OR pure **Appium 2.x** with Java client.
- **Mobile Driver**: `appium-uiautomator2-driver` (Android) or `appium-xcuitest-driver` (iOS).

---

## 2. Installation

### Option A: Katalon Studio (Drop-in JAR)
1. Download the latest JAR: [`locara-1.15.1.jar`](https://github.com/Qureon/Locara/releases).
2. Copy the file into your Katalon project's `Drivers/` directory:
   ```bash
   cp locara-1.15.1.jar /path/to/YourProject/Drivers/
   ```
3. Restart or refresh Katalon Studio (**Project** → **Refresh Drivers**).

### Option B: Gradle Dependency
If you manage your automation framework with Gradle, add Locara as a local JAR dependency:
```groovy
dependencies {
    implementation files('Drivers/locara-1.15.1.jar')
}
```

---

## 3. Configuration (`locara.properties`)

Create a `locara.properties` configuration file in your project root:

```properties
# Locara Engine Configuration
locara.enabled=true
locara.target=AUTO
locara.threshold=0.70
locara.weight.text=0.35
locara.weight.id=0.25
locara.weight.desc=0.20
locara.scroll.enabled=true
locara.scroll.max_attempts=2
locara.scroll.manual_guard.enabled=true
locara.scroll.manual_cooldown_ms=15000
locara.audit.enabled=true
locara.framework.adaptive=true
```

### Parameter Reference

| Key | Default | Description |
| :--- | :--- | :--- |
| `locara.enabled` | `true` | Globally enables or disables in-flight healing. |
| `locara.target` | `AUTO` | Heuristic preset: `AUTO`, `UMUM`, `REVAMP`, or `KMP` (legacy alias `locara.target.profile` also accepted). |
| `locara.threshold` | `0.70` | Minimum confidence score (0.0 – 1.0) required to trigger healing (`locara.similarity.threshold` alias supported). |
| `locara.scroll.enabled` | `true` | Automatically scrolls off-screen form elements into view when healing misses. |
| `locara.scroll.max_attempts` | `2` | Maximum vertical scroll attempts per healing recovery cycle. |
| `locara.scroll.manual_guard.enabled` | `true` | Prevents double scrolling by suppressing auto-scroll immediately after manual `swipe`/`scrollToText`/`notifyManualScroll()` actions. |
| `locara.scroll.manual_cooldown_ms` | `15000` | Window (ms, evaluated at action start) after a manual scroll during which auto-scroll stays suppressed (`0` disables timer). |
| `locara.audit.enabled` | `true` | Writes PII-masked telemetry events to `Include/evidence/locara_audit.json` & `.md`. |
