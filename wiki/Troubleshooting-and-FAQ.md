# 💡 Troubleshooting & FAQ

---

### Q1: Does Locara alter my original Object Repository files (`.rs`)?
**No.** Locara strictly respects the **Strict Object Repository Anti-Tampering** standard. It performs in-flight memory healing during test execution without modifying your repository files on disk.

---

### Q2: How does Locara differ from Healenium?
- **Healenium** operates as an external network proxy over HTTP on ports `:8085`/`:7878`, focusing primarily on Selenium/Appium DOM trees.
- **Locara** runs **in-process (in-flight)** inside your Katalon or Appium test thread. It combines 2D spatial vector alignment, field relationship parsing, and overlay airbag dismissal with sub-15ms overhead. They complement each other seamlessly!

---

### Q3: What should I do if a healed selector targets the wrong element?
Adjust the similarity threshold in `locara.properties`:
```properties
# Raise threshold from default 0.70 to 0.85 for stricter matching
locara.threshold=0.85
```

---

### Q4: Is Locara compatible with iOS XCUITest?
**Yes.** Locara supports both Android (UiAutomator2) and iOS (XCUITest). 2D spatial alignment and overlay dismissal functions adapt automatically to iOS accessibility hierarchies.

---

### Q5: Why does `getText()` / `getAttribute("text")` return empty on KMP Compose `*_error` elements?
In Jetpack Compose / KMP, `testTag` on an error container (`*_error`) is attached to the outer `android.view.View` (`text=""`), while the actual red validation message lives inside a child `android.widget.TextView`. Use descendant text extraction (`MobileElementUtil.safeGetText()` or `//*[@resource-id="..."]//android.widget.TextView`) to read validation text reliably across both ID and EN locales.

---

### Q6: How should tests handle Android 14+ `permissioncontroller`, `intentresolver` ("Open with"), and `autofill_save` overlays?
- **Runtime Permissions**: Support both `com.google.android.permissioncontroller:id/*` and `com.android.permissioncontroller:id/*`, and treat both `permission_deny_button` and `permission_deny_and_dont_ask_again_button` (including curly apostrophe `"Don’t allow"`) as deny guards.
- **SMS App Chooser (`com.android.intentresolver`)**: Dismiss via the top dim area (`dialog_dim_bg`) or return to the target app package before invoking DEV/UAT OTP bypass endpoints.
- **Google Password Manager (`android:id/autofill_save`)**: Tap `android:id/autofill_save_no` (`"Never"`) after first-time login so the sheet does not obscure dashboard assertions.
