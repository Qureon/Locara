# 🏛️ Architecture & Healing Engines

Locara operates on an **in-flight autonomous recovery loop**. When a test execution script requests an element via an Object Repository locator (`.rs`) or Appium locator (`By`), Locara wraps the driver call with an active interceptor.

---

## 🔄 The In-Flight Healing Lifecycle

```
       [ Script calls Driver / Keyword ]
                      │
                      ▼
            [ Original Selector Valid? ]
             ├── YES ──► [ Execute Action (No Latency) ]
             │
             └── NO (NoSuchElementException / Timeout)
                      │
                      ▼
         [ Autonomous Airbag Check ]
         (Detects & dismisses blocking modals)
                      │
                      ▼
         [ Multi-Dimensional Heuristic Analysis ]
         ├── 1. 2D Spatial Centroid & Bounding Box
         ├── 2. Field-Label Relationship Traverser
         └── 3. Profile-Specific Strategy (KMP/REVAMP/UMUM)
                      │
                      ▼
         [ Confidence >= Threshold? ]
             ├── YES ──► [ Self-Heal, Log Audit & Execute Action ]
             └── NO  ──► [ Fail gracefully with Forensic Snapshot ]
```

---

## 🧠 Core Engines

### 1. Universal 2D Spatial Alignment
Mobile UIs frequently alter internal resource IDs, testIDs, or class structures while retaining their relative spatial positioning on screen. 
- **Centroid Vectoring**: Calculates Euclidean distance between the last-known screen coordinates and current candidates.
- **Bounding Box Aspect Matcher**: Preserves button height/width ratio thresholds to prevent mis-targeting adjacent icons.
- **Quadrant Pinning**: Bounds candidates within the same logical quadrant (e.g., Top-Right Action Bar, Bottom Navigation Bar).

### 2. Field Relationship Parser
Form inputs (e.g., Account Number, Transfer Amount, Password) often lack stable identifiers. Locara traverses adjacent sibling nodes:
- **`ABOVE_LABEL`**: Input field directly above a descriptive caption.
- **`LEFT_OF_ACTION`**: Input field placed to the left of an inline toggle or verify button.
- **`CONTAINER_SIBLING`**: Input field sharing the same Card/Form parent container with a static label.

### 3. Autonomous Overlay Airbag
Unexpected overlays (in-app promotions, system permission dialogs, rating prompts, session timeout warnings) are the #1 cause of flaky failures.
- Intercepts non-clickable exceptions.
- Matches known close buttons (`ic_close`, `btn_cancel`, `Tutup`, `Nanti Saja`, `Allow`).
- Dismisses the airbag automatically, verifies target element visibility, and resumes the test sequence without human intervention.\n