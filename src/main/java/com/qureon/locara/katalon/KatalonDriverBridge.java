package com.qureon.locara.katalon;

import com.qureon.locara.core.model.UIElementNode;
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
            boolean isComplexXPath = com.qureon.locara.core.generator.KatalonRsSynthesizer.isComplexOrRelationalXPath(node, healedXPath);
            if (node != null && !isComplexXPath) {
                if (node.hasContentDesc()) {
                    strategyName = "ACCESSIBILITY";
                    locatorValue = node.getContentDesc();
                } else if (node.hasResourceId()) {
                    strategyName = "ID";
                    locatorValue = node.getResourceId();
                } else if (node.hasText()) {
                    strategyName = "ATTRIBUTES";
                    String textLit = com.qureon.locara.core.generator.DynamicXPathGenerator.toXPathLiteral(node.getText());
                    locatorValue = "//*[@class = '" + node.getClassName() + "' and (@text = " + textLit + " or . = " + textLit + ")]";
                }
            }

            // 2. Set Mobile locator & strategy if supported (MobileTestObject)
            try {
                Class<?> lsEnum = Class.forName("com.kms.katalon.core.enums.mobile.LocatorStrategy");
                Object strategyVal = resolveEnumConstant(lsEnum, strategyName);

                invokeOptional(toClass, testObject, "setLocatorStrategy", new Class<?>[]{lsEnum}, strategyVal);
                invokeOptional(toClass, testObject, "setLocator", new Class<?>[]{String.class}, locatorValue);
                invokeOptional(toClass, testObject, "setMobileLocator", new Class<?>[]{String.class}, locatorValue);
                invokeOptional(toClass, testObject, "setLocatorValue", new Class<?>[]{lsEnum, String.class}, strategyVal, locatorValue);
            } catch (Exception ignored) {
                try {
                    Class<?> mlsEnum = Class.forName("com.kms.katalon.core.testobject.MobileTestObject$MobileLocatorStrategy");
                    Object mlsVal = resolveEnumConstant(mlsEnum, strategyName);
                    Method setMobileLocatorStrategy = toClass.getMethod("setMobileLocatorStrategy", mlsEnum);
                    setMobileLocatorStrategy.invoke(testObject, mlsVal);

                    Method setMobileLocator = toClass.getMethod("setMobileLocator", String.class);
                    setMobileLocator.invoke(testObject, locatorValue);
                } catch (Exception ignored2) {}
            }

            // 3. Fallback: Set selector method to XPATH
            try {
                Class<?> smEnum = Class.forName("com.kms.katalon.core.testobject.SelectorMethod");
                Object xpathMethodVal = resolveEnumConstant(smEnum, "XPATH");
                Method setMethod = toClass.getMethod("setSelectorMethod", smEnum);
                setMethod.invoke(testObject, xpathMethodVal);

                Method setValue = toClass.getMethod("setSelectorValue", smEnum, String.class);
                setValue.invoke(testObject, xpathMethodVal, healedXPath);
            } catch (Exception ignored) {}

            // 4. Populate / update rich properties
            try {
                Class<?> ctEnum = Class.forName("com.kms.katalon.core.testobject.ConditionType");
                Object equalsVal = resolveEnumConstant(ctEnum, "EQUALS");
                Class<?> propClass = Class.forName("com.kms.katalon.core.testobject.TestObjectProperty");
                Method addProp = toClass.getMethod("addProperty", propClass);
                java.lang.reflect.Constructor<?> propCtor = propClass.getConstructor(String.class, ctEnum, String.class, boolean.class);

                addTestObjectProperty(testObject, addProp, propCtor, equalsVal, "xpath", healedXPath);
                if (node != null) {
                    if (node.hasContentDesc()) {
                        addTestObjectProperty(testObject, addProp, propCtor, equalsVal, "content-desc", node.getContentDesc());
                    }
                    if (node.hasText()) {
                        addTestObjectProperty(testObject, addProp, propCtor, equalsVal, "text", node.getText());
                    }
                    if (node.hasResourceId()) {
                        addTestObjectProperty(testObject, addProp, propCtor, equalsVal, "resource-id", node.getResourceId());
                    }
                    if (!node.getClassName().isEmpty()) {
                        addTestObjectProperty(testObject, addProp, propCtor, equalsVal, "class", node.getClassName());
                    }
                }
            } catch (Exception ignored) {}

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static int[] getScreenDimensions(Object driver, int defaultWidth, int defaultHeight) {
        if (driver != null) {
            try {
                Method manageMethod = driver.getClass().getMethod("manage");
                Object windowOptions = manageMethod.invoke(driver);
                Method windowMethod = windowOptions.getClass().getMethod("window");
                Object window = windowMethod.invoke(windowOptions);
                Method getSizeMethod = window.getClass().getMethod("getSize");
                Object size = getSizeMethod.invoke(window);
                Method getWidthMethod = size.getClass().getMethod("getWidth");
                Method getHeightMethod = size.getClass().getMethod("getHeight");
                int w = (int) getWidthMethod.invoke(size);
                int h = (int) getHeightMethod.invoke(size);
                if (w > 0 && h > 0) {
                    return new int[]{w, h};
                }
            } catch (Throwable ignored) {}
        }
        return new int[]{defaultWidth, defaultHeight};
    }

    private static Object resolveEnumConstant(Class<?> enumClass, String constantName) {
        if (enumClass == null || !enumClass.isEnum() || constantName == null) {
            return null;
        }
        Object[] constants = enumClass.getEnumConstants();
        if (constants != null) {
            for (Object constant : constants) {
                if (constant instanceof Enum<?> enumConst && enumConst.name().equals(constantName)) {
                    return constant;
                }
            }
        }
        throw new IllegalArgumentException("No enum constant " + enumClass.getName() + "." + constantName);
    }

    private static void invokeOptional(Class<?> targetClass, Object instance, String methodName,
                                       Class<?>[] paramTypes, Object... args) {
        try {
            Method m = targetClass.getMethod(methodName, paramTypes);
            m.invoke(instance, args);
        } catch (Exception ignored) {}
    }

    private static void addTestObjectProperty(Object testObject, Method addProp,
                                              java.lang.reflect.Constructor<?> propCtor,
                                              Object equalsVal, String name, String value) throws Exception {
        Object prop = propCtor.newInstance(name, equalsVal, value, true);
        addProp.invoke(testObject, prop);
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
