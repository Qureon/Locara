package com.qureon.locara.katalon;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.logging.LocaraLogger;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import com.qureon.locara.core.model.UIElementNode;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Executes autonomous scroll and carousel swipe gestures during self-healing recovery
 * while delegating eligibility policies to {@link ScrollEligibilityPolicy} and honoring {@link ManualScrollGuard}.
 */
public class ScrollRecoveryEngine {

    private static final int DEFAULT_SCREEN_WIDTH = 720;
    private static final int DEFAULT_SCREEN_HEIGHT = 1280;
    private static final int EDGE_MARGIN_PX = 50;
    private static final int MIN_GESTURE_BOX_PX = 100;
    private static final long VERTICAL_SETTLE_MS = 600L;
    private static final long SHORT_SETTLE_MS = 400L;
    private static final long HORIZONTAL_SETTLE_MS = 500L;
    private static final double GESTURE_PERCENT = 0.75;

    private final int maxScrollAttempts;

    public ScrollRecoveryEngine() { this(2); }

    public ScrollRecoveryEngine(int maxScrollAttempts) {
        this.maxScrollAttempts = maxScrollAttempts;
    }

    public int getMaxScrollAttempts() { return maxScrollAttempts; }

    public boolean isOverlayOrDialogTarget(String hint, String targetRsPath) {
        return ScrollEligibilityPolicy.isOverlayOrDialogTarget(hint, targetRsPath);
    }

    public boolean isScrollEligible(String hint, String targetRsPath, String action, List<UIElementNode> nodes) {
        return ScrollEligibilityPolicy.isScrollEligible(hint, targetRsPath, action, nodes);
    }

    public boolean hasScrollableContainer(List<UIElementNode> nodes) {
        return ScrollEligibilityPolicy.hasScrollableContainer(nodes);
    }

    public boolean hasHorizontalCarousel(List<UIElementNode> nodes) {
        return ScrollEligibilityPolicy.hasHorizontalCarousel(nodes);
    }

    public boolean isCarouselEligible(String hint, String targetRsPath, String action, List<UIElementNode> nodes) {
        return ScrollEligibilityPolicy.isCarouselEligible(hint, targetRsPath, action, nodes);
    }

    public HealResult recoverWithScroll(
            HealConfig config, LocaraEngine engine, HealContext context,
            List<UIElementNode> initialNodes, String initialPageSource, HealResult initialResult,
            Object driver, Supplier<String> pageSourceSupplier, String logPrefix) {
        if (initialResult != null && initialResult.isSuccess()) return initialResult;
        if ((config != null && !config.isScrollRecoveryEnabled()) || maxScrollAttempts <= 0
                || engine == null || context == null || pageSourceSupplier == null) {
            return initialResult;
        }

        String prefix = (logPrefix != null && !logPrefix.isEmpty()) ? logPrefix : "[Locara by Qureon]";
        String hint = context.getTargetHint();
        if (!isScrollEligible(hint, context.getTargetRsPath(), context.getAction(), initialNodes)) {
            if (isOverlayOrDialogTarget(hint, context.getTargetRsPath())) {
                LocaraLogger.info(prefix, "🛡️ Target '" + hint + "' is an overlay/dialog. Auto-scroll inhibited.");
            }
            return initialResult;
        }

        if (ManualScrollGuard.isAutoScrollSuppressed(config)) {
            LocaraLogger.info(prefix, "🛑 Auto-scroll skipped for '" + hint + "': recent manual scroll/swipe already performed by test script.");
            return initialResult;
        }

        Set<String> seenSources = new HashSet<>();
        if (initialPageSource != null && !initialPageSource.isEmpty()) {
            seenSources.add(initialPageSource);
        }

        HealResult latest = initialResult;
        try {
            for (int attempt = 1; attempt <= maxScrollAttempts; attempt++) {
                LocaraLogger.info(prefix, "📜 Target '" + hint + "' not found in viewport. Auto-scroll attempt #" + attempt + "...");
                if (!performScrollDown(driver)) break;

                String scrolledSource = pageSourceSupplier.get();
                if (scrolledSource == null || scrolledSource.isEmpty()) break;
                if (!seenSources.add(scrolledSource)) {
                    LocaraLogger.info(prefix, "⏹️ Viewport unchanged after scroll #" + attempt + " (boundary reached). Stopping.");
                    break;
                }
                latest = engine.heal(scrolledSource, context);
                if (latest.isSuccess()) {
                    LocaraLogger.info(prefix, "🎯 Target '" + hint + "' discovered after scroll recovery!");
                    return latest;
                }
            }
        } catch (Exception ignored) {}
        return latest;
    }

    public boolean performScrollDown(Object driver) {
        if (driver == null) return false;
        int[] dims = KatalonDriverBridge.getScreenDimensions(driver, DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT);
        int width = dims[0], height = dims[1];
        int startY = Math.min(height - EDGE_MARGIN_PX, (int) (height * 0.70));
        int endY = Math.max(EDGE_MARGIN_PX, (int) (height * 0.25));
        if (invokeKatalonSwipe(width / 2, startY, width / 2, endY, VERTICAL_SETTLE_MS)) return true;
        return invokeAppiumGesture(driver, "mobile: scrollGesture", "down",
                (int) (height * 0.20), Math.max(MIN_GESTURE_BOX_PX, width - 2 * EDGE_MARGIN_PX),
                Math.max(MIN_GESTURE_BOX_PX, (int) (height * 0.60)), GESTURE_PERCENT, VERTICAL_SETTLE_MS);
    }

    public boolean performShortScroll(Object driver) {
        if (driver == null) return false;
        int[] dims = KatalonDriverBridge.getScreenDimensions(driver, DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT);
        return invokeKatalonSwipe(dims[0] / 2, (int) (dims[1] * 0.55), dims[0] / 2, (int) (dims[1] * 0.40), SHORT_SETTLE_MS);
    }

    public boolean performScrollUp(Object driver) {
        if (driver == null) return false;
        int[] dims = KatalonDriverBridge.getScreenDimensions(driver, DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT);
        return invokeKatalonSwipe(dims[0] / 2, (int) (dims[1] * 0.30), dims[0] / 2, (int) (dims[1] * 0.70), HORIZONTAL_SETTLE_MS);
    }

    public boolean performSwipeLeft(Object driver) {
        return performHorizontalSwipe(driver, true);
    }

    public boolean performSwipeRight(Object driver) {
        return performHorizontalSwipe(driver, false);
    }

    private boolean performHorizontalSwipe(Object driver, boolean toLeft) {
        if (driver == null) return false;
        int[] dims = KatalonDriverBridge.getScreenDimensions(driver, DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT);
        int width = dims[0], height = dims[1];
        int rightX = Math.min(width - EDGE_MARGIN_PX, (int) (width * 0.85));
        int leftX = Math.max(EDGE_MARGIN_PX, (int) (width * 0.15));
        int startX = toLeft ? rightX : leftX;
        int endX = toLeft ? leftX : rightX;
        if (invokeKatalonSwipe(startX, height / 2, endX, height / 2, HORIZONTAL_SETTLE_MS)) return true;
        return invokeAppiumGesture(driver, "mobile: swipeGesture", toLeft ? "left" : "right",
                (int) (height * 0.30), Math.max(MIN_GESTURE_BOX_PX, width - 2 * EDGE_MARGIN_PX),
                Math.max(MIN_GESTURE_BOX_PX, (int) (height * 0.40)), GESTURE_PERCENT, HORIZONTAL_SETTLE_MS);
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
            if (!jsExecutor.isInstance(driver)) return false;
            Method executeScript = jsExecutor.getMethod("executeScript", String.class, Object[].class);
            Map<String, Object> args = new HashMap<>();
            args.put("left", EDGE_MARGIN_PX);
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
