package com.qureon.locara.katalon;

import com.qureon.locara.core.model.UIElementNode;

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

    private static final java.util.Set<String> OVERLAY_AND_DIALOG_KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "popup", "pop_up", "pop up", "pop-up", "pop",
            "modal", "dialog", "alert", "prompt", "overlay", "airbag",
            "izin", "perizinan", "permission", "permissions", "notifikasi", "notification", "notif",
            "lokasi", "location", "alamat", "address", "gps",
            "allow", "izinkan", "deny", "tolak", "block", "jangan izinkan",
            "batal", "cancel", "tutup", "close", "dismiss", "later", "nanti",
            "bottomsheet", "bottom_sheet", "snackbar", "toast", "banner", "promo",
            "system_dialog", "runtime_permission", "grant_permissions"
    ));

    public int getMaxScrollAttempts() {
        return maxScrollAttempts;
    }

    /**
     * Identifies whether the target hint or Object Repository path represents a transient
     * pop-up modal, permission request, notification dialog, or system overlay.
     * Overlays reside on separate floating window layers and MUST NEVER trigger viewport scrolling.
     */
    public boolean isOverlayOrDialogTarget(String hint, String targetRsPath) {
        String combined = ((hint != null ? hint : "") + " " + (targetRsPath != null ? targetRsPath : "")).toLowerCase();
        if (combined.contains("permissioncontroller") || combined.contains("permission_allow_button")
                || combined.contains("com.android.permissioncontroller") || combined.contains("com.google.android.gms.location")
                || combined.contains("pop up") || combined.contains("pop-up") || combined.contains("bottom sheet")) {
            return true;
        }

        String[] tokens = combined.split("[\\s_\\-./]+");
        for (String token : tokens) {
            if (OVERLAY_AND_DIALOG_KEYWORDS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates whether scroll recovery is safe and eligible for the given action and target.
     * Prevents "ngide" scrolling when probing conditional elements, checking presence, or hunting for pop-ups.
     */
    public boolean isScrollEligible(String hint, String targetRsPath, String action, List<UIElementNode> nodes) {
        if (action == null || action.trim().isEmpty()) {
            return false;
        }

        String act = action.trim().toUpperCase();

        // 1. Presence checks, probes, assertions, and checkpoints MUST NEVER scroll (strictly read-only)
        if (act.contains("VERIFY") || act.contains("WAIT") || act.startsWith("IS_")
                || act.contains("ASSERT") || act.contains("PRESENT") || act.contains("VISIBLE")
                || act.contains("PROBE") || act.contains("CHECKPOINT") || act.contains("GET_TEXT")) {
            return false;
        }

        // 2. Overlays, Modals, System Permission Dialogs MUST NEVER scroll
        if (isOverlayOrDialogTarget(hint, targetRsPath)) {
            return false;
        }

        // 3. Must have an active scrollable container in the current view
        return hasScrollableContainer(nodes);
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

    /**
     * Performs a short, controlled downward scroll to reveal elements without overshooting.
     */
    public boolean performShortScroll(Object driver) {
        if (driver == null) return false;
        int[] dims = getScreenDimensions(driver);
        int width = dims[0];
        int height = dims[1];

        int startX = width / 2;
        int startY = (int) (height * 0.55);
        int endX = width / 2;
        int endY = (int) (height * 0.40);

        try {
            Class<?> mobileKeywords = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
            Method swipeMethod = mobileKeywords.getMethod("swipe", int.class, int.class, int.class, int.class);
            swipeMethod.invoke(null, startX, startY, endX, endY);
            Thread.sleep(400);
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Performs an upward scroll (swipe top to bottom) to recover overshot elements.
     */
    public boolean performScrollUp(Object driver) {
        if (driver == null) return false;
        int[] dims = getScreenDimensions(driver);
        int width = dims[0];
        int height = dims[1];

        int startX = width / 2;
        int startY = (int) (height * 0.30);
        int endX = width / 2;
        int endY = (int) (height * 0.70);

        try {
            Class<?> mobileKeywords = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
            Method swipeMethod = mobileKeywords.getMethod("swipe", int.class, int.class, int.class, int.class);
            swipeMethod.invoke(null, startX, startY, endX, endY);
            Thread.sleep(500);
            return true;
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
