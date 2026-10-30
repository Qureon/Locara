# 🛡️ Welcome to the Locara Wiki

<div align="center">

### Adaptive Locator Intelligence
*Next-generation in-flight autonomous self-healing engine for mobile test automation.*

[![Version](https://img.shields.io/badge/version-1.5.0-00E5FF?style=flat-square&logo=semver)](https://github.com/Qureon/Locara)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg?style=flat-square)](https://github.com/Qureon/Locara/blob/main/LICENSE)
[![Java](https://img.shields.io/badge/java-17%20%7C%2021%20%7C%2024-orange?style=flat-square&logo=openjdk)](https://github.com/Qureon/Locara)
[![Katalon](https://img.shields.io/badge/katalon-8.x%20%7C%209.x%20%7C%2010.x-00B0FF?style=flat-square)](https://katalon.com)
[![Appium](https://img.shields.io/badge/appium-2.x%20%7C%201.x-662d91?style=flat-square&logo=appium)](https://appium.io)

</div>

---

## 📌 Overview

**Locara** *(Locator + Recovery)* is a high-performance, in-flight autonomous self-healing library engineered by **Qureon**. It intercepts broken UI locators during live test execution, recalculates targets dynamically via multi-dimensional spatial heuristics, and ensures zero pipeline failures from minor front-end layout or selector changes.

Unlike traditional proxy-based healing tools that operate externally over HTTP, Locara runs **in-process (in-flight)** inside your test runner runtime, introducing sub-15ms healing latency without requiring external daemon infrastructure.

---

## 🗺️ Documentation Map

Explore the topics below to get up and running:

| Section | Description |
| :--- | :--- |
| **[🚀 Getting Started](Getting-Started)** | Installation, Fat JAR deployment, and `locara.properties` configuration. |
| **[🏛️ Architecture & Engines](Architecture-and-Healing-Engines)** | 2D Spatial Alignment, Field Relationship Parsing, and Autonomous Airbags. |
| **[🎯 Target Profiles](Target-Profiles)** | Deep dive into `AUTO`, `UMUM`, `REVAMP`, and `KMP` specialized heuristics. |
| **[🧩 Keywords & API Reference](Keywords-and-API-Reference)** | Ready-to-use Groovy/Java keywords: `smartTap`, `smartSetText`, and auto-healing hooks. |
| **[📊 Mission Control Dashboard](Mission-Control-Dashboard)** | Real-time telemetry, visual recovery logs, and telemetry dashboard server. |
| **[💡 Troubleshooting & FAQ](Troubleshooting-and-FAQ)** | Common setup pitfalls, heuristic sensitivity tuning, and best practices. |

---

## ⚡ Key Highlights in v1.5.0

1. **Universal 2D Spatial Alignment**: Uses Euclidean centroid distance, bounding-box aspect ratio matching, and layout quadrant pinning.
2. **Field Relationship Parser**: Anchors dynamic input fields to adjacent static text labels (supports Above, Left-Of, Below, and Inside-Container relationships).
3. **Autonomous Overlay Airbag**: Automatically identifies and dismisses obstructive popups (permission dialogs, promos, announcements, dismiss buttons) before interacting with targets.
4. **Tri-Target Heuristic Architecture**: Switch seamlessly between `UMUM` (generic cross-app), `REVAMP` (legacy hybrid banking), and `KMP` (modern testID-first banking).
5. **Zero External Dependencies**: Distributed as an ultra-compact, self-contained Fat JAR (`locara-1.5.0.jar`) compatible with Katalon Studio 8.x–10.x and standard Appium 2.x suites.\n