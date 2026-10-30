# 🚀 Getting Started with Locara

This guide walks you through integrating **Locara v1.5.0** into your Katalon Studio project or standard Appium test suite in under 3 minutes.

---

## 1. Prerequisites

- **Java Development Kit (JDK)**: Version 17, 21, or 24 LTS.
- **Katalon Studio Enterprise**: 8.x, 9.x, or 10.x (Mobile Automation License), OR pure **Appium 2.x** with Java client.
- **Mobile Driver**: `appium-uiautomator2-driver` (Android) or `appium-xcuitest-driver` (iOS).

---

## 2. Installation

### Option A: Katalon Studio (Drop-in Fat JAR)
1. Download the latest Fat JAR: [`locara-1.5.0.jar`](https://github.com/Qureon/Locara/releases).
2. Copy the file into your Katalon project's `Drivers/` directory:
   ```bash
   cp locara-1.5.0.jar /path/to/YourProject/Drivers/
   ```
3. Restart or refresh Katalon Studio (**Project** → **Refresh Drivers**).

### Option B: Gradle Dependency
If you manage your automation framework with Gradle, add Locara as a local JAR dependency:
```groovy
dependencies {
    implementation files('Drivers/locara-1.5.0.jar')
}
```

---

## 3. Configuration (`locara.properties`)

Create a `locara.properties` configuration file in your project root:

```properties
# Locara Engine Configuration
locara.enabled=true
locara.target.profile=AUTO
locara.similarity.threshold=0.72
locara.max.search.depth=6
locara.airbag.auto.dismiss=true
locara.telemetry.enabled=true
locara.telemetry.port=8085
```

### Parameter Reference

| Key | Default | Description |
| :--- | :--- | :--- |
| `locara.enabled` | `true` | Globally enables or disables in-flight healing. |
| `locara.target.profile` | `AUTO` | Heuristic preset: `AUTO`, `UMUM`, `REVAMP`, or `KMP`. |
| `locara.similarity.threshold` | `0.72` | Minimum confidence score (0.0 – 1.0) required to trigger healing. |
| `locara.max.search.depth` | `6` | Maximum DOM tree traversal depth when resolving parent-child containers. |
| `locara.airbag.auto.dismiss` | `true` | Automatically detects and closes blocking overlays/promotions. |
| `locara.telemetry.enabled` | `true` | Emits live recovery events to the Mission Control Dashboard. |\n