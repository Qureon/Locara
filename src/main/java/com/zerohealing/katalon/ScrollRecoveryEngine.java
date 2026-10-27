package com.zerohealing.katalon;

import com.zerohealing.core.model.UIElementNode;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Auto-Scroll Recovery Engine:
 * When a target element is off-screen (below the fold), this engine detects
 * scrollable containers and autonomously scrolls down to reveal and heal the element.
 */
public class ScrollRecoveryEngine {

    private final int maxScrollAttempts;

    public ScrollRecoveryEngine() {
        this(2);
    }

    public ScrollRecoveryEngine(int maxScrollAttempts) {
        this.maxScrollAttempts = maxScrollAttempts;
    }

    public int getMaxScrollAttempts() {
        return maxScrollAttempts;
    }

    /**
     * Checks if the parsed node hierarchy has any active scrollable containers.
     */
    public boolean hasScrollableContainer(List<UIElementNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return false;
        for (UIElementNode node : nodes) {
            if (node.isScrollable()) {
                return true;
            }
            String tag = node.getTag().toLowerCase();
            String className = node.getClassName().toLowerCase();
            if (tag.contains("scrollview") || tag.contains("recyclerview") || tag.contains("listview")
                    || className.contains("scrollview") || className.contains("recyclerview")
                    || tag.contains("table") || tag.contains("collectionview")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Performs an autonomous downward scroll (swipe from bottom to top).
     */
    public boolean performScrollDown(Object driver) {
        if (driver == null) return false;

        int[] dims = getScreenDimensions(driver);
        int width = dims[0];
        int height = dims[1];

        int startX = width / 2;
        int startY = Math.min(height - 50, (int) (height * 0.70));
        int endX = width / 2;
        int endY = Math.max(50, (int) (height * 0.25));

        // Try Katalon MobileBuiltInKeywords.swipe(startX, startY, endX, endY)
        try {
            Class<?> mobileKeywords = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
            Method swipeMethod = mobileKeywords.getMethod("swipe", int.class, int.class, int.class, int.class);
            swipeMethod.invoke(null, startX, startY, endX, endY);
            Thread.sleep(600);
            return true;
        } catch (Exception ignored) {}

        // Fallback: Appium UiAutomator2 mobile: scrollGesture with full bounding parameters
        try {
            Class<?> jsExecutor = Class.forName("org.openqa.selenium.JavascriptExecutor");
            if (jsExecutor.isInstance(driver)) {
                Method executeScript = jsExecutor.getMethod("executeScript", String.class, Object[].class);
                java.util.Map<String, Object> scrollArgs = new java.util.HashMap<>();
                scrollArgs.put("left", 50);
                scrollArgs.put("top", (int) (height * 0.20));
                scrollArgs.put("width", Math.max(100, width - 100));
                scrollArgs.put("height", Math.max(100, (int) (height * 0.60)));
                scrollArgs.put("direction", "down");
                scrollArgs.put("percent", 0.75);
                executeScript.invoke(driver, "mobile: scrollGesture", new Object[]{scrollArgs});
                Thread.sleep(600);
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    private int[] getScreenDimensions(Object driver) {
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
        // Conservative default (720x1280 HD) prevents out-of-bounds swipe on smaller displays
        return new int[]{720, 1280};
    }
}
