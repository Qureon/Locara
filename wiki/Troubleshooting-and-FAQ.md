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
# Raise threshold from default 0.72 to 0.85 for stricter matching
locara.similarity.threshold=0.85
```

---

### Q4: Is Locara compatible with iOS XCUITest?
**Yes.** Locara supports both Android (UiAutomator2) and iOS (XCUITest). 2D spatial alignment and overlay dismissal functions adapt automatically to iOS accessibility hierarchies.
