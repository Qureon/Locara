# 🎯 Target Profiles (UMUM, REVAMP, KMP)

Locara features a **Tri-Target Heuristic Architecture** that tailors its recovery algorithms to the exact architectural pattern of the application under test.

---

## 1. Profile Comparison Matrix

| Profile | Target Application | Primary Heuristics | Best Suited For |
| :--- | :--- | :--- | :--- |
| **`AUTO`** | Any / Auto-Detected | DOM signature sniffer; switches between profiles dynamically | Mixed or evolving applications. |
| **`UMUM`** | Generic Mobile Apps | Accessibility ID, Class hierarchy, Text similarity, 2D proximity | E-commerce, Ride-hailing, Fintech, Consumer apps. |
| **`REVAMP`** | React Native Hybrid Apps | Parent-container resolution, nested ViewGroup text/accessibility heuristics | Deeply nested React Native & hybrid mobile architectures. |
| **`KMP`** | Kotlin / Compose Multiplatform | `testID` morphology resolution, canonical screen-element token mapping | Modern Jetpack Compose / SwiftUI / KMP enterprise apps. |

---

## 2. Configuration Examples

### Set via `locara.properties`:
```properties
# Force KMP Profile for Kotlin Multiplatform / Compose Mobile Project
locara.target=KMP
```

### Set Dynamically at Runtime in Java / Groovy:
```groovy
import com.qureon.locara.katalon.Locara

// Switch profile cleanly for the active execution thread
Locara.setAppTarget("KMP")
```
