package com.qureon.locara.katalon;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import com.qureon.locara.core.model.UIElementNode;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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
        return isMutativeNonOverlayTarget(hint, targetRsPath, action) && hasScrollableContainer(nodes);
    }

    private boolean isMutativeNonOverlayTarget(String hint, String targetRsPath, String action) {
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
        return !isOverlayOrDialogTarget(hint, targetRsPath);
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
        return isMutativeNonOverlayTarget(hint, targetRsPath, action) && hasHorizontalCarousel(nodes);
    }

    /**
     * Shared autonomous scroll recovery loop for Katalon and Appium bridges.
     * Respects ManualScrollGuard (prevents double-scrolling after explicit script scrolls)
     * and stops immediately if the viewport/page source stops changing (bottom/boundary reached).
     */
    public HealResult recoverWithScroll(
            HealConfig config,
            LocaraEngine engine,
            HealContext context,
            List<UIElementNode> initialNodes,
            String initialPageSource,
            HealResult initialResult,
            Object driver,
            Supplier<String> pageSourceSupplier,
            String logPrefix) {
        if (initialResult != null && initialResult.isSuccess()) {
            return initialResult;
        }
        if ((config != null && !config.isScrollRecoveryEnabled()) || maxScrollAttempts <= 0
                || engine == null || context == null || pageSourceSupplier == null) {
            return initialResult;
        }

        String hint = context.getTargetHint();
        String rsPath = context.getTargetRsPath();
        String action = context.getAction();
        String prefix = (logPrefix != null && !logPrefix.isEmpty()) ? logPrefix : "[Locara by Qureon]";

        if (!isScrollEligible(hint, rsPath, action, initialNodes)) {
            if (isOverlayOrDialogTarget(hint, rsPath)) {
                System.out.println(prefix + " 🛡️ Target '" + hint + "' identified as Transient Popup / Overlay / Permission Dialog. Auto-scroll strictly inhibited to preserve screen state.");
            }
            return initialResult;
        }

        if (ManualScrollGuard.isAutoScrollSuppressed(config)) {
            System.out.println(prefix + " 🛑 Auto-scroll skipped for '" + hint + "': recent manual scroll/swipe already performed by test script.");
            return initialResult;
        }

        Set<String> seenPageSources = new HashSet<>();
        if (initialPageSource != null && !initialPageSource.isEmpty()) {
            seenPageSources.add(initialPageSource);
        }

        HealResult latestResult = initialResult;
        try {
            for (int attempt = 1; attempt <= maxScrollAttempts; attempt++) {
                System.out.println(prefix + " 📜 Target '" + hint + "' not found in active viewport. Attempting auto-scroll recovery #" + attempt + "...");
                if (!performScrollDown(driver)) {
                    break;
                }

                String scrolledSource = pageSourceSupplier.get();
                if (scrolledSource == null || scrolledSource.isEmpty()) {
                    break;
                }
                if (!seenPageSources.add(scrolledSource)) {
                    System.out.println(prefix + " ⏹️ Screen viewport unchanged after scroll #" + attempt + " (boundary reached). Stopping further auto-scroll.");
                    break;
                }

                latestResult = engine.heal(scrolledSource, context);
                if (latestResult.isSuccess()) {
                    System.out.println(prefix + " 🎯 Target '" + hint + "' successfully discovered after scroll recovery!");
                    return latestResult;
                }
            }
        } catch (Exception ignored) {}

        return latestResult;
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
        ManualScrollGuard.beginInternalScroll();
        try {
            Class<?> mobileKeywords = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
            Method swipeMethod = mobileKeywords.getMethod("swipe", int.class, int.class, int.class, int.class);
            swipeMethod.invoke(null, startX, startY, endX, endY);
        } catch (Exception ignored) {
            return false;
        } finally {
            ManualScrollGuard.endInternalScroll();
        }
        sleepWithInterruptRestore(settleMs);
        return true;
    }

    private static boolean invokeAppiumGesture(Object driver, String gestureName, String direction,
                                                 int top, int areaWidth, int areaHeight, double percent, long settleMs) {
        ManualScrollGuard.beginInternalScroll();
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
        } finally {
            ManualScrollGuard.endInternalScroll();
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
