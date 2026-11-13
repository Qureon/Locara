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
     * Checks if the parsed node hierarchy contains an active horizontal carousel or tab strip container.
     */
    public boolean hasHorizontalCarousel(List<UIElementNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return false;
        for (UIElementNode node : nodes) {
            String tag = node.getTag().toLowerCase();
            String className = node.getClassName().toLowerCase();
            String resId = node.getResourceId().toLowerCase();
            if (tag.contains("horizontalscrollview") || className.contains("horizontalscrollview")
                    || tag.contains("viewpager") || className.contains("viewpager")
                    || tag.contains("tablayout") || className.contains("tablayout")
                    || resId.contains("carousel") || resId.contains("tabs") || resId.contains("slider")
                    || (node.isScrollable() && (resId.contains("horizontal") || tag.contains("horizontal") || className.contains("horizontal")))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates whether horizontal carousel swipe recovery is safe and eligible for the given action and target.
     */
    public boolean isCarouselEligible(String hint, String targetRsPath, String action, List<UIElementNode> nodes) {
        if (action == null || action.trim().isEmpty()) {
            return false;
        }

        String act = action.trim().toUpperCase();

        // 1. Presence checks, probes, assertions, and checkpoints MUST NEVER swipe (strictly read-only)
        if (act.contains("VERIFY") || act.contains("WAIT") || act.startsWith("IS_")
                || act.contains("ASSERT") || act.contains("PRESENT") || act.contains("VISIBLE")
                || act.contains("PROBE") || act.contains("CHECKPOINT") || act.contains("GET_TEXT")) {
            return false;
        }

        // 2. Overlays, Modals, System Permission Dialogs MUST NEVER swipe
        if (isOverlayOrDialogTarget(hint, targetRsPath)) {
            return false;
        }

        // 3. Must have an active horizontal carousel in the current view
        return hasHorizontalCarousel(nodes);
    }

    /**
     * Performs an autonomous downward scroll (swipe from bottom to top).
     */
    public boolean performScrollDown(Object driver) {
        if (driver == null) return false;

        int[] dims = KatalonDriverBridge.getScreenDimensions(driver, 720, 1280);
        int width = dims[0];
        int height = dims[1];

        int startX = width / 2;
        int startY = Math.min(height - 50, (int) (height * 0.70));
        int endX = width / 2;
        int endY = Math.max(50, (int) (height * 0.25));

        if (invokeKatalonSwipe(startX, startY, endX, endY, 600)) {
            return true;
        }
        return invokeAppiumGesture(driver, "mobile: scrollGesture", "down",
                (int) (height * 0.20), Math.max(100, width - 100), Math.max(100, (int) (height * 0.60)), 0.75, 600);
    }

    /**
     * Performs a short, controlled downward scroll to reveal elements without overshooting.
     */
    public boolean performShortScroll(Object driver) {
        if (driver == null) return false;
        int[] dims = KatalonDriverBridge.getScreenDimensions(driver, 720, 1280);
        int width = dims[0];
        int height = dims[1];

        return invokeKatalonSwipe(width / 2, (int) (height * 0.55), width / 2, (int) (height * 0.40), 400);
    }

    /**
     * Performs an upward scroll (swipe top to bottom) to recover overshot elements.
     */
    public boolean performScrollUp(Object driver) {
        if (driver == null) return false;
        int[] dims = KatalonDriverBridge.getScreenDimensions(driver, 720, 1280);
        int width = dims[0];
        int height = dims[1];

        return invokeKatalonSwipe(width / 2, (int) (height * 0.30), width / 2, (int) (height * 0.70), 500);
    }

    /**
     * Performs an autonomous leftward swipe (drag from right to left)
     * to reveal next cards or tabs in a horizontal carousel.
     */
    public boolean performSwipeLeft(Object driver) {
        if (driver == null) return false;
        int[] dims = KatalonDriverBridge.getScreenDimensions(driver, 720, 1280);
        int width = dims[0];
        int height = dims[1];

        int startX = Math.min(width - 50, (int) (width * 0.85));
        int endX = Math.max(50, (int) (width * 0.15));
        if (invokeKatalonSwipe(startX, height / 2, endX, height / 2, 500)) {
            return true;
        }
        return invokeAppiumGesture(driver, "mobile: swipeGesture", "left",
                (int) (height * 0.30), Math.max(100, width - 100), Math.max(100, (int) (height * 0.40)), 0.75, 500);
    }

    /**
     * Performs an autonomous rightward swipe (drag from left to right)
     * to reveal previous cards or tabs in a horizontal carousel.
     */
    public boolean performSwipeRight(Object driver) {
        if (driver == null) return false;
        int[] dims = KatalonDriverBridge.getScreenDimensions(driver, 720, 1280);
        int width = dims[0];
        int height = dims[1];

        int startX = Math.max(50, (int) (width * 0.15));
        int endX = Math.min(width - 50, (int) (width * 0.85));
        if (invokeKatalonSwipe(startX, height / 2, endX, height / 2, 500)) {
            return true;
        }
        return invokeAppiumGesture(driver, "mobile: swipeGesture", "right",
                (int) (height * 0.30), Math.max(100, width - 100), Math.max(100, (int) (height * 0.40)), 0.75, 500);
    }

    private static boolean invokeKatalonSwipe(int startX, int startY, int endX, int endY, long settleMs) {
        try {
            Class<?> mobileKeywords = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
            Method swipeMethod = mobileKeywords.getMethod("swipe", int.class, int.class, int.class, int.class);
            swipeMethod.invoke(null, startX, startY, endX, endY);
        } catch (Exception ignored) {
            return false;
        }
        sleepWithInterruptRestore(settleMs);
        return true;
    }

    private static boolean invokeAppiumGesture(Object driver, String gestureName, String direction,
                                                 int top, int areaWidth, int areaHeight, double percent, long settleMs) {
        try {
            Class<?> jsExecutor = Class.forName("org.openqa.selenium.JavascriptExecutor");
            if (!jsExecutor.isInstance(driver)) {
                return false;
            }
            Method executeScript = jsExecutor.getMethod("executeScript", String.class, Object[].class);
            java.util.Map<String, Object> args = new java.util.HashMap<>();
            args.put("left", 50);
            args.put("top", top);
            args.put("width", areaWidth);
            args.put("height", areaHeight);
            args.put("direction", direction);
            args.put("percent", percent);
            executeScript.invoke(driver, gestureName, new Object[]{args});
        } catch (Exception ignored) {
            return false;
        }
        sleepWithInterruptRestore(settleMs);
        return true;
    }

    private static void sleepWithInterruptRestore(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
