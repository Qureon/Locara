package com.zerohealing.katalon;

import com.zerohealing.core.model.UIElementNode;

import java.lang.reflect.Method;

/**
 * The Golden Triad Keyboard Guard:
 * Detects whether a target mobile UI element is obscured by a soft keyboard,
 * and performs resilient automatic keyboard dismissal before tap/click actions.
 */
public class KeyboardGuard {

    private static final double DEFAULT_KEYBOARD_THRESHOLD_RATIO = 0.58;

    public KeyboardGuard() {}

    /**
     * Determines whether the given element resides in the bottom viewport zone
     * typically occluded by an active soft keyboard.
     */
    public boolean isPotentiallyObscured(UIElementNode node, int screenHeight) {
        if (node == null) return false;
        int effectiveHeight = screenHeight > 0 ? screenHeight : 2400;
        int elementBottomY = node.getBounds().y + node.getBounds().height;
        return elementBottomY >= (effectiveHeight * DEFAULT_KEYBOARD_THRESHOLD_RATIO);
    }

    /**
     * Safely dismisses the soft keyboard using multiple fallback strategies.
     */
    public boolean dismissKeyboard(Object driver) {
        if (driver == null) return false;

        // Strategy 1: Katalon MobileBuiltInKeywords.hideKeyboard()
        try {
            Class<?> mobileKeywords = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
            Method hideMethod = mobileKeywords.getMethod("hideKeyboard");
            hideMethod.invoke(null);
            Thread.sleep(300);
            return true;
        } catch (Exception ignored) {}

        // Strategy 2: Appium Driver driver.hideKeyboard()
        try {
            Method hideMethod = driver.getClass().getMethod("hideKeyboard");
            hideMethod.invoke(driver);
            Thread.sleep(300);
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Checks if the soft keyboard is currently open on the mobile device.
     */
    public boolean isKeyboardShown(Object driver) {
        if (driver == null) return false;
        try {
            Method isShownMethod = driver.getClass().getMethod("isKeyboardShown");
            Object res = isShownMethod.invoke(driver);
            if (res instanceof Boolean) {
                return (Boolean) res;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Guards an element before interaction: dynamically calculates screen height and dismisses keyboard if obscured.
     */
    public void guardElement(Object driver, UIElementNode node) {
        int height = getScreenHeight(driver);
        guardElement(driver, node, height);
    }

    /**
     * Guards an element before interaction: if obscured, automatically dismisses the keyboard.
     * Performs an active isKeyboardShown verification to prevent sending accidental 'Back' key signals.
     */
    public void guardElement(Object driver, UIElementNode node, int screenHeight) {
        if (isPotentiallyObscured(node, screenHeight)) {
            // Only dismiss if the keyboard is actually confirmed open, or if an input field is active
            if (isKeyboardShown(driver)) {
                System.out.println("[Zero-Healing] ⌨️ Active soft keyboard detected obscuring '" + node.getStrippedResourceId() + "'. Dismissing keyboard...");
                dismissKeyboard(driver);
            }
        }
    }

    private int getScreenHeight(Object driver) {
        if (driver != null) {
            try {
                Method manageMethod = driver.getClass().getMethod("manage");
                Object windowOptions = manageMethod.invoke(driver);
                Method windowMethod = windowOptions.getClass().getMethod("window");
                Object window = windowMethod.invoke(windowOptions);
                Method getSizeMethod = window.getClass().getMethod("getSize");
                Object size = getSizeMethod.invoke(window);
                Method getHeightMethod = size.getClass().getMethod("getHeight");
                int h = (int) getHeightMethod.invoke(size);
                if (h > 0) return h;
            } catch (Throwable ignored) {}
        }
        return 2400;
    }
}
