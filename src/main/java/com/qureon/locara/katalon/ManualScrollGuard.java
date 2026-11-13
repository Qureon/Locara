package com.qureon.locara.katalon;

import com.qureon.locara.core.model.HealConfig;

import java.util.function.LongSupplier;

/**
 * Thread-local coordinator that tracks recent manual scroll/swipe activity performed by test scripts
 * so autonomous ScrollRecoveryEngine recovery does not perform redundant "double scrolling"
 * immediately after an explicit ScrollUtil / Mobile.swipe / Mobile.scrollToText action.
 */
public final class ManualScrollGuard {

    public static final long DEFAULT_COOLDOWN_MS = HealConfig.DEFAULT_MANUAL_SCROLL_COOLDOWN_MS;
    public static final int DEFAULT_MAX_POST_SCROLL_TAPS = 2;

    private static volatile LongSupplier clock = System::currentTimeMillis;

    private static final ThreadLocal<ScrollState> STATE = ThreadLocal.withInitial(ScrollState::new);

    private ManualScrollGuard() {}

    private static final class ScrollState {
        int internalScrollDepth = 0;
        long lastManualScrollAtMs = -1L;
        int successfulTapsSinceScroll = 0;
        boolean actionWindowActive = false;
        boolean actionWindowSuppressed = false;
        boolean pendingTapSuccess = false;
    }

    /**
     * Scoped token that freezes manual-scroll eligibility at action entry (before Katalon/Appium
     * spends 5-30s polling a broken locator and hitting timeout before entering Locara healing).
     */
    public static final class ActionWindow implements AutoCloseable {
        private final boolean previousActive;
        private final boolean previousSuppressed;
        private boolean closed = false;

        private ActionWindow(boolean previousActive, boolean previousSuppressed) {
            this.previousActive = previousActive;
            this.previousSuppressed = previousSuppressed;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            ScrollState state = STATE.get();
            if (!previousActive && state.pendingTapSuccess) {
                state.pendingTapSuccess = false;
                recordSuccessfulTap();
            }
            state.actionWindowActive = previousActive;
            state.actionWindowSuppressed = previousSuppressed;
            if (!previousActive && state.lastManualScrollAtMs < 0L && state.internalScrollDepth <= 0) {
                STATE.remove();
            }
        }
    }

    /**
     * Marks entry into Locara's own autonomous swipe/scroll gesture so keyword interceptors
     * do not misclassify internal healing swipes as user-script manual scrolls.
     */
    public static void beginInternalScroll() {
        STATE.get().internalScrollDepth++;
    }

    public static void endInternalScroll() {
        ScrollState state = STATE.get();
        if (state.internalScrollDepth > 0) {
            state.internalScrollDepth--;
        }
    }

    public static boolean isInternalScrollActive() {
        return STATE.get().internalScrollDepth > 0;
    }

    /**
     * Records that the caller/script just performed an explicit manual scroll or swipe.
     * Ignored when invoked while Locara itself is executing an internal scroll recovery gesture.
     */
    public static void recordManualScroll() {
        ScrollState state = STATE.get();
        if (state.internalScrollDepth > 0) {
            return;
        }
        state.lastManualScrollAtMs = clock.getAsLong();
        state.successfulTapsSinceScroll = 0;
    }

    /**
     * Tracks successful tap interactions after a manual scroll so suppression lifts once
     * the test progresses past post-scroll confirmation actions onto subsequent screens.
     */
    public static void recordSuccessfulTap() {
        ScrollState state = STATE.get();
        if (state.lastManualScrollAtMs < 0L) {
            return;
        }
        state.successfulTapsSinceScroll++;
        if (state.successfulTapsSinceScroll >= DEFAULT_MAX_POST_SCROLL_TAPS) {
            clearManualScrollTiming(state);
        }
    }

    /**
     * Flags that a tap action succeeded within the current window; recorded once when the
     * outermost ActionWindow closes (or immediately if no ActionWindow is active) so nested wrappers never double-count.
     */
    public static void markActionTapSucceeded() {
        ScrollState state = STATE.get();
        if (state.actionWindowActive) {
            state.pendingTapSuccess = true;
        } else {
            recordSuccessfulTap();
        }
    }

    /**
     * Opens an action-scoped manual-scroll evaluation window prior to primary locator lookup.
     */
    public static ActionWindow openActionWindow(HealConfig config) {
        boolean guardEnabled = config == null || config.isManualScrollGuardEnabled();
        long cooldownMs = config != null ? config.getManualScrollCooldownMs() : DEFAULT_COOLDOWN_MS;
        ScrollState state = STATE.get();
        boolean prevActive = state.actionWindowActive;
        boolean prevSuppressed = state.actionWindowSuppressed;
        if (!prevActive) {
            state.pendingTapSuccess = false;
        }
        boolean suppressedNow = prevActive
                ? prevSuppressed
                : evaluateCooldown(state, guardEnabled, cooldownMs);
        state.actionWindowActive = true;
        state.actionWindowSuppressed = suppressedNow;
        return new ActionWindow(prevActive, prevSuppressed);
    }

    /**
     * Returns true when autonomous viewport scrolling should be skipped because the script
     * already performed a manual scroll within the configured window on this thread.
     */
    public static boolean isAutoScrollSuppressed(HealConfig config) {
        boolean guardEnabled = config == null || config.isManualScrollGuardEnabled();
        if (!guardEnabled) {
            return false;
        }
        long cooldownMs = config != null ? config.getManualScrollCooldownMs() : DEFAULT_COOLDOWN_MS;
        if (cooldownMs <= 0L) {
            return false;
        }
        ScrollState state = STATE.get();
        if (state.actionWindowActive) {
            return state.actionWindowSuppressed;
        }
        return evaluateCooldown(state, true, cooldownMs);
    }

    /**
     * Clears all ThreadLocal scroll tracking state for the current thread.
     */
    public static void clear() {
        STATE.remove();
    }

    static void setClockForTests(LongSupplier testClock) {
        clock = testClock != null ? testClock : System::currentTimeMillis;
    }

    static void resetClock() {
        clock = System::currentTimeMillis;
    }

    private static boolean evaluateCooldown(ScrollState state, boolean guardEnabled, long cooldownMs) {
        if (!guardEnabled || cooldownMs <= 0L || state.lastManualScrollAtMs < 0L) {
            return false;
        }
        if (state.successfulTapsSinceScroll >= DEFAULT_MAX_POST_SCROLL_TAPS) {
            clearManualScrollTiming(state);
            return false;
        }
        long elapsed = clock.getAsLong() - state.lastManualScrollAtMs;
        if (elapsed < 0L || elapsed > cooldownMs) {
            clearManualScrollTiming(state);
            return false;
        }
        return true;
    }

    private static void clearManualScrollTiming(ScrollState state) {
        state.lastManualScrollAtMs = -1L;
        state.successfulTapsSinceScroll = 0;
    }
}
