package com.zerohealing.katalon;

import com.zerohealing.core.model.UIElementNode;
import java.lang.reflect.Method;

/**
 * Dynamic reflection bridge to Katalon Studio's MobileDriverFactory and Appium driver.
 * Ensures zero-coupling with proprietary Katalon jars at compile time.
 */
public class KatalonDriverBridge {

    public static Object getMobileDriver() {
        try {
            Class<?> factoryClz = Class.forName("com.kms.katalon.core.mobile.keyword.internal.MobileDriverFactory");
            Method getDriverMethod = factoryClz.getMethod("getDriver");
            Object drv = getDriverMethod.invoke(null);
            if (drv != null) return drv;
        } catch (Exception ignored) {}

        try {
            Class<?> admClz = Class.forName("com.kms.katalon.core.appium.driver.AppiumDriverManager");
            Method getDriverMethod = admClz.getMethod("getDriver");
            Object drv = getDriverMethod.invoke(null);
            if (drv != null) return drv;
        } catch (Exception ignored) {}

        try {
            Class<?> dfClz = Class.forName("com.kms.katalon.core.webui.driver.DriverFactory");
            Method getWebDriver = dfClz.getMethod("getWebDriver");
            return getWebDriver.invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String getPageSource(Object driver) {
        if (driver == null) return null;
        try {
            Method getPageSourceMethod = driver.getClass().getMethod("getPageSource");
            Object res = getPageSourceMethod.invoke(driver);
            return res != null ? res.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isKatalonRuntime() {
        try {
            Class.forName("com.kms.katalon.core.testobject.TestObject");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Mutates a Katalon TestObject in-memory with the healed locator, strategy, and rich properties.
     */
    public static boolean patchTestObject(Object testObject, UIElementNode node, String healedXPath) {
        if (testObject == null || healedXPath == null) return false;
        try {
            Class<?> toClass = testObject.getClass();

            // 1. Determine strategy and locator value
            String strategyName = "XPATH";
            String locatorValue = healedXPath;
            if (node != null) {
                if (node.hasContentDesc()) {
                    strategyName = "ACCESSIBILITY";
                    locatorValue = node.getContentDesc();
                } else if (node.hasResourceId()) {
                    strategyName = "ID";
                    locatorValue = node.getResourceId();
                } else if (node.hasText()) {
                    strategyName = "ATTRIBUTES";
                    locatorValue = "//*[@class = '" + node.getClassName() + "' and (@text = '" + node.getText() + "' or . = '" + node.getText() + "')]";
                }
            }

            // 2. Set Mobile locator & strategy if supported (MobileTestObject)
            try {
                Class<?> lsEnum = Class.forName("com.kms.katalon.core.enums.mobile.LocatorStrategy");
                Object strategyVal = Enum.valueOf((Class<Enum>) lsEnum, strategyName);

                try {
                    Method setLocatorStrategy = toClass.getMethod("setLocatorStrategy", lsEnum);
                    setLocatorStrategy.invoke(testObject, strategyVal);
                } catch (Exception ignored) {}

                try {
                    Method setLocator = toClass.getMethod("setLocator", String.class);
                    setLocator.invoke(testObject, locatorValue);
                } catch (Exception ignored) {}

                try {
                    Method setMobileLocator = toClass.getMethod("setMobileLocator", String.class);
                    setMobileLocator.invoke(testObject, locatorValue);
                } catch (Exception ignored) {}

                try {
                    Method setLocatorValue = toClass.getMethod("setLocatorValue", lsEnum, String.class);
                    setLocatorValue.invoke(testObject, strategyVal, locatorValue);
                } catch (Exception ignored) {}
            } catch (Exception ignored) {
                try {
                    Class<?> mlsEnum = Class.forName("com.kms.katalon.core.testobject.MobileTestObject$MobileLocatorStrategy");
                    Object mlsVal = Enum.valueOf((Class<Enum>) mlsEnum, strategyName);
                    Method setMobileLocatorStrategy = toClass.getMethod("setMobileLocatorStrategy", mlsEnum);
                    setMobileLocatorStrategy.invoke(testObject, mlsVal);

                    Method setMobileLocator = toClass.getMethod("setMobileLocator", String.class);
                    setMobileLocator.invoke(testObject, locatorValue);
                } catch (Exception ignored2) {}
            }

            // 3. Fallback: Set selector method to XPATH
            try {
                Class<?> smEnum = Class.forName("com.kms.katalon.core.testobject.SelectorMethod");
                Object xpathMethodVal = Enum.valueOf((Class<Enum>) smEnum, "XPATH");
                Method setMethod = toClass.getMethod("setSelectorMethod", smEnum);
                setMethod.invoke(testObject, xpathMethodVal);

                Method setValue = toClass.getMethod("setSelectorValue", smEnum, String.class);
                setValue.invoke(testObject, xpathMethodVal, healedXPath);
            } catch (Exception ignored) {}

            // 4. Populate / update rich properties
            try {
                Class<?> ctEnum = Class.forName("com.kms.katalon.core.testobject.ConditionType");
                Object equalsVal = Enum.valueOf((Class<Enum>) ctEnum, "EQUALS");
                Class<?> propClass = Class.forName("com.kms.katalon.core.testobject.TestObjectProperty");
                Method addProp = toClass.getMethod("addProperty", propClass);

                // Add xpath property
                Object xpathProp = propClass.getConstructor(String.class, ctEnum, String.class, boolean.class)
                        .newInstance("xpath", equalsVal, healedXPath, true);
                addProp.invoke(testObject, xpathProp);

                if (node != null) {
                    if (node.hasContentDesc()) {
                        Object cdProp = propClass.getConstructor(String.class, ctEnum, String.class, boolean.class)
                                .newInstance("content-desc", equalsVal, node.getContentDesc(), true);
                        addProp.invoke(testObject, cdProp);
                    }
                    if (node.hasText()) {
                        Object textProp = propClass.getConstructor(String.class, ctEnum, String.class, boolean.class)
                                .newInstance("text", equalsVal, node.getText(), true);
                        addProp.invoke(testObject, textProp);
                    }
                    if (node.hasResourceId()) {
                        Object idProp = propClass.getConstructor(String.class, ctEnum, String.class, boolean.class)
                                .newInstance("resource-id", equalsVal, node.getResourceId(), true);
                        addProp.invoke(testObject, idProp);
                    }
                    if (!node.getClassName().isEmpty()) {
                        Object clsProp = propClass.getConstructor(String.class, ctEnum, String.class, boolean.class)
                                .newInstance("class", equalsVal, node.getClassName(), true);
                        addProp.invoke(testObject, clsProp);
                    }
                }
            } catch (Exception ignored) {}

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean patchTestObject(Object testObject, String healedXPath) {
        return patchTestObject(testObject, null, healedXPath);
    }

    /**
     * Creates a new in-memory Katalon TestObject on the fly with the given locator and node.
     */
    public static Object createDynamicTestObject(String objectId, UIElementNode node, String healedXPath) {
        try {
            Class<?> toClass = Class.forName("com.kms.katalon.core.testobject.TestObject");
            Object to = toClass.getConstructor(String.class).newInstance(objectId);
            patchTestObject(to, node, healedXPath);
            return to;
        } catch (Exception e) {
            return null;
        }
    }

    public static Object createDynamicTestObject(String objectId, String healedXPath) {
        return createDynamicTestObject(objectId, null, healedXPath);
    }
}
