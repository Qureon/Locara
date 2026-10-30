<div align="center">

# Qureon
### Engineering Intelligence.

*Building intelligent, resilient, and adaptive engineering systems for modern test automation.*

[![GitHub Organization](https://img.shields.io/badge/organization-Qureon-00E5FF?style=flat-square&logo=github)](https://github.com/Qureon)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

</div>

---

## About Qureon

**Qureon** is an engineering organization focused on solving mission-critical reliability challenges in software testing and quality automation. We research, design, and build intelligent infrastructure that eliminates flaky tests, reduces maintenance overhead, and ensures deterministic software delivery at scale.

```
                   Q U R E O N
              Engineering Intelligence
                         │
        ┌────────────────┴────────────────┐
        ▼                                 ▼
     Locara                           Ecosystem
Adaptive Locator Intelligence    Docs · Tooling · Bridges
```

---

## Flagship Project: Locara

### Adaptive Locator Intelligence
*An intelligent self-healing engine for resilient mobile and web test automation.*

**Locara** *(Locator + Recovery)* is Qureon's first open-source project. It bridges the gap between fast-moving UI iterations and fragile test automation suites by introducing **in-flight, zero-interruption healing** for Katalon Studio and Appium.

* **Repository**: [`Qureon/Locara`](https://github.com/Qureon/Locara)
* **Status**: Production Ready (`v1.3.0`)
* **Core Philosophy**: Zero Code Changes · Zero Flaky Interruption · Strict Safety Polarity

### Key Capabilities
* **In-Flight Recovery (<50ms)**: Dynamically resolves modified or missing UI locators during live execution without interrupting the test run.
* **Instant Artifact Synthesis**: Automatically generates and updates physical `.rs` Object Repository entities directly to disk.
* **Danger Polarity Guard**: Prevents false-positive actions on destructive elements (e.g. *Cancel*, *Delete*, *Block*).
* **Viewport & Keyboard Resilience**: Automatically compensates for hidden elements below the fold or obscured by software keyboards.

---

## Open-Source Ecosystem Roadmap

We are incrementally rolling out our modular test automation ecosystem:

| Repository | Purpose | Status |
|---|---|---|
| **[`Locara`](https://github.com/Qureon/Locara)** | Core autonomous self-healing engine & instant `.rs` synthesizer | Active (v1.3.0) |
| **`Locara-Docs`** | Comprehensive guides, API reference, and architecture deep-dives | Planned |
| **`Locara-Examples`** | Production-grade sample projects (Katalon, Appium Java, Cucumber) | Planned |
| **`.github`** | Organization profile, community standards, and workflow templates | Active |

---

## Engineering Values

1. **Deterministic Resilience**: Automation should adapt to change without masking genuine business regressions.
2. **Zero Friction**: Integration should never force engineering teams to rewrite existing suites or learn proprietary domain-specific languages.
3. **Safety by Design**: Self-healing must enforce strict boundaries to prevent accidental destructive operations in enterprise environments.

---

## Connect & Contribute

* **Organization**: [github.com/Qureon](https://github.com/Qureon)
* **Issue Tracker**: [github.com/Qureon/Locara/issues](https://github.com/Qureon/Locara/issues)
* **License**: Open source under the [MIT License](https://opensource.org/licenses/MIT).
