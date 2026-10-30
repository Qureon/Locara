# 🎯 Target Profiles (UMUM, REVAMP, KMP)

Locara features a **Tri-Target Heuristic Architecture** that tailors its recovery algorithms to the exact architectural pattern of the application under test.

---

## 1. Profile Comparison Matrix

| Profile | Target Application | Primary Heuristics | Best Suited For |
| :--- | :--- | :--- | :--- |
| **`AUTO`** | Any / Auto-Detected | DOM signature sniffer; switches between profiles dynamically | Mixed or evolving applications. |
| **`UMUM`** | Generic Mobile Apps | Accessibility ID, Class hierarchy, Text similarity, 2D proximity | E-commerce, Ride-hailing, Fintech, Consumer apps. |
| **`REVAMP`** | Legacy Hybrid Banking | Relative XPath, Nested Frame recovery, Text regex matching | Complex hybrid Cordova/WebView banking architectures. |
| **`KMP`** | Modern Katalon Mobile | `testID` first, canonical QA pack mapping, atomic screen flows | Modern Jetpack Compose / SwiftUI / React Native enterprise apps. |

---

## 2. Configuration Examples

### Set via `locara.properties`:
```properties
# Force KMP Profile for Bank Mega Modern Mobile Project
locara.target.profile=KMP
```

### Set Dynamically at Runtime in Groovy / Java:
```groovy
import com.qureon.locara.katalon.LocaraKeywords

// Switch profile on-the-fly during test suite execution
LocaraKeywords.setTargetProfile("KMP")
```\n