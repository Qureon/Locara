package com.qureon.locara.core.driver;

import com.qureon.locara.core.logging.LocaraLogger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;

/**
 * Shared zero-dependency reflection bridge for Appium / Selenium WebDriver element
 * discovery, timeout polling, actions, and text/visibility inspection.
 * Eliminates duplicate reflection logic between Katalon {@code Locara} and {@code AppiumLocara}.
 */
public final class AppiumDriverActionBridge {

    private static final String LOG_PREFIX = "[Locara DriverBridge]";
    private static final long POLL_INTERVAL_MS = 250L;

    private static volatile Class<?> cachedByClass;
    private static volatile Method cachedByXPathMethod;
    private static volatile Method cachedByIdMethod;

    private AppiumDriverActionBridge() {}

    public static String extractPageSource(Object driver) {
        if (driver == null) {
            return null;
        }
        try {
            Method getPageSourceMethod = findAccessibleMethod(driver.getClass(), "getPageSource");
            Object res = getPageSourceMethod.invoke(driver);
            return res != null ? res.toString() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static Object findElementDirectly(Object driver, String locatorOrXPath) {
        if (driver == null || locatorOrXPath == null || locatorOrXPath.trim().isEmpty()) {
            return null;
        }
        try {
            Object by = createBy(locatorOrXPath);
            Method findElementMethod = findAccessibleMethod(driver.getClass(), "findElement", getByClass());
            return findElementMethod.invoke(driver, by);
        } catch (ClassNotFoundException e) {
            return findElementWithoutSeleniumApi(driver, locatorOrXPath);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static Object findElementWithTimeout(Object driver, String xpath, int timeoutSeconds) throws Exception {
        if (driver == null) {
            throw new IllegalArgumentException("Driver instance cannot be null");
        }
        long maxWaitMs = Math.max(0, timeoutSeconds) * 1000L;
        long deadline = System.currentTimeMillis() + maxWaitMs;
        ReflectiveOperationException lastException = null;

        while (true) {
            try {
                Object by = createByXPath(xpath);
                Method findElementMethod = findAccessibleMethod(driver.getClass(), "findElement", getByClass());
                Object found = findElementMethod.invoke(driver, by);
                if (found != null) {
                    return found;
                }
            } catch (ClassNotFoundException e) {
                Object fallback = findElementWithoutSeleniumApi(driver, xpath);
                if (fallback != null) {
                    return fallback;
                }
            } catch (ReflectiveOperationException e) {
                lastException = e;
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) {
                if (lastException != null) {
                    throw lastException;
                }
                throw new RuntimeException("Element not found for locator: " + xpath);
            }
            try {
                Thread.sleep(Math.min(POLL_INTERVAL_MS, remaining));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw lastException != null ? lastException : ie;
            }
        }
    }

    public static List<?> findElementsByXPath(Object driver, String xpath) throws ReflectiveOperationException {
        if (driver == null || xpath == null || xpath.isEmpty()) {
            return Collections.emptyList();
        }
        Object by = createByXPath(xpath);
        Method findElementsMethod = findAccessibleMethod(driver.getClass(), "findElements", getByClass());
        Object result = findElementsMethod.invoke(driver, by);
        return result instanceof List<?> list ? list : Collections.emptyList();
    }

    public static boolean executeElementAction(Object element, String action, String value)
            throws ReflectiveOperationException {
        if (element == null) {
            return false;
        }
        Class<?> cls = element.getClass();
        if ("TAP".equalsIgnoreCase(action) || "CLICK".equalsIgnoreCase(action)) {
            findAccessibleMethod(cls, "click").invoke(element);
            return true;
        }
        if ("SET_TEXT".equalsIgnoreCase(action)) {
            findAccessibleMethod(cls, "clear").invoke(element);
            findAccessibleMethod(cls, "sendKeys", CharSequence[].class)
                    .invoke(element, (Object) new CharSequence[]{value != null ? value : ""});
            return true;
        }
        if ("CLEAR_TEXT".equalsIgnoreCase(action)) {
            findAccessibleMethod(cls, "clear").invoke(element);
            return true;
        }
        return true;
    }

    public static String readElementText(Object element) throws ReflectiveOperationException {
        if (element == null) {
            return "";
        }
        Class<?> cls = element.getClass();
        Object textObj = findAccessibleMethod(cls, "getText").invoke(element);
        String text = textObj != null ? textObj.toString() : "";
        if (!text.isEmpty()) {
            return text;
        }

        Method getAttributeMethod = findAccessibleMethod(cls, "getAttribute", String.class);
        Object attrTextObj = getAttributeMethod.invoke(element, "text");
        if (attrTextObj != null && !attrTextObj.toString().isEmpty()) {
            return attrTextObj.toString();
        }

        Object descTextObj = getAttributeMethod.invoke(element, "content-desc");
        return descTextObj != null ? descTextObj.toString() : text;
    }

    public static boolean verifyElementExists(Object driver, String xpath) {
        if (driver == null || xpath == null || xpath.isEmpty()) {
            return false;
        }
        try {
            List<?> elements = findElementsByXPath(driver, xpath);
            return elements != null && !elements.isEmpty();
        } catch (Throwable t) {
            LocaraLogger.warn(LOG_PREFIX, "Element existence verification failed: " + t.getMessage());
            return false;
        }
    }

    public static boolean verifyElementDisplayed(Object driver, String xpath) {
        if (driver == null || xpath == null || xpath.isEmpty()) {
            return false;
        }
        try {
            List<?> elements = findElementsByXPath(driver, xpath);
            if (elements != null && !elements.isEmpty()) {
                Object el = elements.get(0);
                Object visible = findAccessibleMethod(el.getClass(), "isDisplayed").invoke(el);
                return Boolean.TRUE.equals(visible);
            }
            return false;
        } catch (Throwable t) {
            LocaraLogger.warn(LOG_PREFIX, "Element visibility verification failed: " + t.getMessage());
            return false;
        }
    }

    private static Object findElementWithoutSeleniumApi(Object driver, String locatorOrXPath) {
        try {
            Method m = findAccessibleMethod(driver.getClass(), "findElementByXPath", String.class);
            return m.invoke(driver, locatorOrXPath);
        } catch (ReflectiveOperationException ignored) {
            try {
                Method m = findAccessibleMethod(driver.getClass(), "findElement", Object.class);
                return m.invoke(driver, locatorOrXPath);
            } catch (ReflectiveOperationException ignored2) {
                return null;
            }
        }
    }

    /**
     * Resolves a {@link Method} preferring a public superclass or interface declaration so
     * anonymous or package-private test/proxy subclasses remain callable across package boundaries.
     */
    public static Method findAccessibleMethod(Class<?> targetClass, String name, Class<?>... paramTypes)
            throws NoSuchMethodException {
        Method direct = targetClass.getMethod(name, paramTypes);
        if (Modifier.isPublic(direct.getDeclaringClass().getModifiers())) {
            return direct;
        }
        for (Class<?> cur = targetClass.getSuperclass(); cur != null && cur != Object.class; cur = cur.getSuperclass()) {
            if (Modifier.isPublic(cur.getModifiers())) {
                try {
                    return cur.getMethod(name, paramTypes);
                } catch (NoSuchMethodException ignored) {}
            }
        }
        for (Class<?> iface : targetClass.getInterfaces()) {
            if (Modifier.isPublic(iface.getModifiers())) {
                try {
                    return iface.getMethod(name, paramTypes);
                } catch (NoSuchMethodException ignored) {}
            }
        }
        return direct;
    }

    private static Object createBy(String locatorOrXPath) throws ReflectiveOperationException {
        if (locatorOrXPath.startsWith("//") || locatorOrXPath.startsWith("/")
                || locatorOrXPath.startsWith("xpath=") || locatorOrXPath.startsWith("(")) {
            String cleanXpath = locatorOrXPath.startsWith("xpath=") ? locatorOrXPath.substring(6) : locatorOrXPath;
            return createByXPath(cleanXpath);
        }
        Method idMethod = cachedByIdMethod;
        if (idMethod == null) {
            idMethod = getByClass().getMethod("id", String.class);
            cachedByIdMethod = idMethod;
        }
        return idMethod.invoke(null, locatorOrXPath);
    }

    private static Object createByXPath(String xpath) throws ReflectiveOperationException {
        Method m = cachedByXPathMethod;
        if (m == null) {
            m = getByClass().getMethod("xpath", String.class);
            cachedByXPathMethod = m;
        }
        return m.invoke(null, xpath);
    }

    private static Class<?> getByClass() throws ClassNotFoundException {
        Class<?> cls = cachedByClass;
        if (cls == null) {
            cls = Class.forName("org.openqa.selenium.By");
            cachedByClass = cls;
        }
        return cls;
    }
}
