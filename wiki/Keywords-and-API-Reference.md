# 🧩 Keywords & API Reference

Locara provides both zero-code automatic healing (via test listeners) and explicit helper keywords for advanced scenario orchestration.

---

## 📦 Package: `com.qureon.locara.katalon`

### Class: `LocaraKeywords`

#### 1. `smartTap(TestObject to, int timeoutSeconds = 10)`
Performs an intelligent tap on the target element with automatic self-healing and overlay airbag dismissal.
```groovy
import com.qureon.locara.katalon.LocaraKeywords
import com.kms.katalon.core.testobject.ObjectRepository

TestObject btnTransfer = findTestObject('Object Repository/Transfer/btn_Lanjut')
LocaraKeywords.smartTap(btnTransfer, 10)
```

#### 2. `smartSetText(TestObject to, String text, int timeoutSeconds = 10)`
Safely inputs text into the target field, utilizing the Field Relationship Parser if the original input selector is broken.
```groovy
LocaraKeywords.smartSetText(findTestObject('Object Repository/Auth/input_Password'), 'MegaPass123', 10)
```

#### 3. `dismissBlockingOverlays()`
Explicitly inspects the screen for unexpected system alerts or promotional popups and dismisses them if detected.
```groovy
LocaraKeywords.dismissBlockingOverlays()
```

#### 4. `healSelector(TestObject to)`
Manually triggers healing resolution for a `TestObject` and returns the newly healed Appium `WebElement`.
```groovy
WebElement healedElement = LocaraKeywords.healSelector(findTestObject('Object Repository/Home/icon_QRIS'))
```\n