# 🧩 Keywords & API Reference

Locara provides both zero-code automatic healing (via test listeners) and explicit helper keywords for advanced scenario orchestration.

---

## 📦 Package: `com.qureon.locara.katalon`

### Class: `LocaraKeywords`

#### 1. `smartTap(TestObject to, String targetHint = "")` & `tap(TestObject to, int timeoutSeconds = 10, String targetHint = "")`
Performs an intelligent tap on the target element with automatic self-healing, keyboard protection, and scroll fallback.
```groovy
import core.LocaraKeywords
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

TestObject btnTransfer = findTestObject('Object Repository/Transfer/btn_Lanjut')
new LocaraKeywords().smartTap(btnTransfer, 'Lanjut')
```

#### 2. `smartSetText(TestObject to, String text, int timeoutSeconds = 10, String targetHint = "")`
Safely inputs text into the target field, utilizing the Field Relationship Parser if the original input selector is broken.
```groovy
new LocaraKeywords().smartSetText(findTestObject('Object Repository/Auth/input_Password'), 'DemoPass123!', 10, 'Password')
```

#### 3. `tapByHint(String targetHint, String saveRsPath = null, int timeoutSeconds = 10)`
Zero-Spy navigation directly by semantic intent hint, optionally synthesizing a Katalon `.rs` file.
```groovy
new LocaraKeywords().tapByHint('Konfirmasi Bayar', 'Object Repository/Payment/btn_Konfirmasi', 10)
```

#### 4. Core Java Overlay & Engine API (`com.qureon.locara.katalon.Locara`)
For programmatic overlay dismissal or direct XPath recovery inside helpers:
```groovy
import com.qureon.locara.katalon.Locara
import com.qureon.locara.core.guard.OverlayAirbag

new OverlayAirbag().dismissBlockingOverlay(driver, pageSource)
Locara.tapByHint('Icon QRIS', 'Object Repository/Home/icon_QRIS')
```
