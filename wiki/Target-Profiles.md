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

---

## 3. Validated KMP / Jetpack Compose Empirical Patterns

Observed and empirically verified during live Android 14+ (HyperOS/MIUI) & Compose Multiplatform (`testTagsAsResourceId = true`) reconnaissance:

1. **Compose Error Container Pattern (`*_error` with `text=""`)**:
   - Inline validation nodes (`register_phone_input_error`, `register_account_number_input_error`, `register_account_password_confirm_input_error`, `lock_password_error`, etc.) are rendered as parent `android.view.View` containers with empty `text=""`, wrapping a child `android.widget.TextView` that holds the red validation message.
   - Always extract error copy via descendant `TextView` fallback (e.g. `MobileElementUtil.safeGetText()`), never raw `getAttribute("text")` on the container alone.
2. **Non-`_alert_` Dialog Suffixes vs Full-Page Failed States**:
   - Modal error dialogs in KMP do not exclusively use `*_alert_message` / `*_alert_ok_button`. Verified modal exceptions include `*_problem_message`/`*_problem_ok_button` (`my_account_spt_problem_*`), `*_error_message`/`*_error_ok_button` (`valas_error_*`, `face_error_*`, `kalkulator_error_*`), and `statement_failed_*` / `liveness_failed_*` / `register_account_confirm_failed_*`.
   - Do **not** blindly wildcard `*_failed_message` as modal alerts, because full-page error states (e.g., `my_account_failed_message`, `transfer_hub_failed_message`) share that suffix without an OK button.
3. **Android 14+ System Overlays & Choosers**:
   - Runtime permission controller package on Android 14+ Google builds is `com.google.android.permissioncontroller` (in addition to `com.android.permissioncontroller`), second-prompt deny uses `permission_deny_and_dont_ask_again_button`, and `"Don’t allow"` uses Unicode curly apostrophe `U+2019`.
   - Triggering SMS registration opens `com.android.intentresolver` (`"Open with"` chooser with `dialog_dim_bg`); first-time login triggers Google Password Manager `android:id/autofill_save` (`autofill_save_no` = `"Never"`).
4. **Compose Checkbox Safe-Left-Tap Invariant**:
   - Legal agreement checkboxes (`register_account_terms_checkbox`, `register_account_password_terms_checkbox`) span the full width including embedded terms text links. Tapping the center risks opening the terms modal instead of toggling `checked=true`; always tap the left-side checkbox box (`x + 8%` width) and guard against unchecking an already-checked box.
5. **Post-Alert Form Reset Asymmetry**:
   - After backend rejection alerts (e.g. `[REG14]`), KMP automatically clears text inputs (`register_account_number_input_input`, `register_account_pin_input_input`) while keeping the terms checkbox `checked=true`.
6. **Live Build vs Catalog (`qa-pack`) Drift**:
   - Always verify live dumps alongside static testID catalogs: live builds expose `register_account_back_button`, `register_phone_back_button`, `register_account_confirm_back_button`, `register_account_email_back_button`, and render `register_email_sent_message` / `register_email_later_button` on the account-registration success modal.
