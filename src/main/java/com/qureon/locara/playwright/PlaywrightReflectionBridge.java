package com.qureon.locara.playwright;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reflection bridge isolating Microsoft Playwright Page and Locator dynamic lookups
 * so Locara operates with zero compile-time Playwright dependency.
 */
final class PlaywrightReflectionBridge {

    private static final Object UNSUPPORTED_OPTIONS = new Object();

    private PlaywrightReflectionBridge() {}

    static Object findLocator(Object page, String selector) {
        if (page == null || selector == null || selector.trim().isEmpty()) {
            return null;
        }
        try {
            return page.getClass().getMethod("locator", String.class).invoke(page, selector);
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean invokeClick(Object locator, int timeoutMs) {
        try {
            Object res = invokeWithTimeoutOptions(locator, "click",
                    "com.microsoft.playwright.Locator$ClickOptions", timeoutMs, new Class<?>[0]);
            if (res != UNSUPPORTED_OPTIONS) return true;
            locator.getClass().getMethod("click").invoke(locator);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean invokeFill(Object locator, String text, int timeoutMs) {
        try {
            Object res = invokeWithTimeoutOptions(locator, "fill",
                    "com.microsoft.playwright.Locator$FillOptions", timeoutMs, new Class<?>[]{String.class}, text);
            if (res != UNSUPPORTED_OPTIONS) return true;
            locator.getClass().getMethod("fill", String.class).invoke(locator, text);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static Object invokeTextContent(Object locator, int timeoutMs) throws Exception {
        Object res = invokeWithTimeoutOptions(locator, "textContent",
                "com.microsoft.playwright.Locator$TextContentOptions", timeoutMs, new Class<?>[0]);
        if (res != UNSUPPORTED_OPTIONS) {
            return res;
        }
        return locator.getClass().getMethod("textContent").invoke(locator);
    }

    static boolean invokeIsVisible(Object locator) {
        try {
            return Boolean.TRUE.equals(locator.getClass().getMethod("isVisible").invoke(locator));
        } catch (Throwable ignored) {
            return false;
        }
    }

    static int invokeCount(Object locator) {
        try {
            Object res = locator.getClass().getMethod("count").invoke(locator);
            return res instanceof Number ? ((Number) res).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    static String extractHtmlContent(Object page) {
        try {
            Object res = page.getClass().getMethod("content").invoke(page);
            return res != null ? res.toString() : "";
        } catch (Throwable t) {
            return null;
        }
    }

    static String extractPageTitleOrUrl(Object page) {
        try {
            Object title = page.getClass().getMethod("title").invoke(page);
            if (title != null && !title.toString().trim().isEmpty()) {
                return title.toString().trim();
            }
            Object url = page.getClass().getMethod("url").invoke(page);
            if (url != null) return url.toString();
        } catch (Throwable ignored) {}
        return "PlaywrightWebPage";
    }

    private static Object invokeWithTimeoutOptions(Object locator, String methodName, String optionsClassName,
                                                   int timeoutMs, Class<?>[] prefixTypes, Object... prefixArgs) {
        if (timeoutMs <= 0) {
            return UNSUPPORTED_OPTIONS;
        }
        try {
            Class<?> optsClz = Class.forName(optionsClassName);
            Object opts = optsClz.getDeclaredConstructor().newInstance();
            optsClz.getMethod("setTimeout", double.class).invoke(opts, (double) timeoutMs);

            Class<?>[] paramTypes = new Class<?>[prefixTypes.length + 1];
            System.arraycopy(prefixTypes, 0, paramTypes, 0, prefixTypes.length);
            paramTypes[prefixTypes.length] = optsClz;

            Object[] args = new Object[prefixArgs.length + 1];
            System.arraycopy(prefixArgs, 0, args, 0, prefixArgs.length);
            args[prefixArgs.length] = opts;

            Method methodWithOpts = locator.getClass().getMethod(methodName, paramTypes);
            return methodWithOpts.invoke(locator, args);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return UNSUPPORTED_OPTIONS;
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
