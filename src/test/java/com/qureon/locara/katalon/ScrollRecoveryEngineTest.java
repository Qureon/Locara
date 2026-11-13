package com.qureon.locara.katalon;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import com.qureon.locara.core.model.UIElementNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class ScrollRecoveryEngineTest {

    private final ScrollRecoveryEngine scrollEngine = new ScrollRecoveryEngine(2);

    @Test
    @DisplayName("Should detect transient popups, overlays, and system permission dialogs accurately")
    void testOverlayAndDialogTargetDetection() {
        // Real-world tester scenarios (bilingual Indonesian & English)
        assertTrue(scrollEngine.isOverlayOrDialogTarget("pop up izin notifikasi sama alamat", "Object Repository/SF/pop_izin"));
        assertTrue(scrollEngine.isOverlayOrDialogTarget("izin notifikasi", ""));
        assertTrue(scrollEngine.isOverlayOrDialogTarget("izin lokasi", "btn_allow_location"));
        assertTrue(scrollEngine.isOverlayOrDialogTarget("pop up promo", "btn_close_promo"));
        assertTrue(scrollEngine.isOverlayOrDialogTarget("modal_dialog_konfirmasi", "btn_batal"));
        assertTrue(scrollEngine.isOverlayOrDialogTarget("system alert", "com.android.permissioncontroller:id/permission_allow_button"));
        assertTrue(scrollEngine.isOverlayOrDialogTarget("bottom sheet pilihan rekening", ""));

        // Regular form inputs and buttons MUST NOT be classified as overlays
        assertFalse(scrollEngine.isOverlayOrDialogTarget("Nomor Rekening Tujuan", "Object Repository/Transfer/input_rekening"));
        assertFalse(scrollEngine.isOverlayOrDialogTarget("Lanjut Transfer", "Object Repository/Transfer/btn_lanjut"));
        assertFalse(scrollEngine.isOverlayOrDialogTarget("btn_bayar_sekarang", "Object Repository/Payment/btn_bayar"));
    }

    @Test
    @DisplayName("Presence checks, probes, and checkpoints MUST NEVER trigger auto-scroll")
    void testScrollEligibilityForProbesAndCheckpoints() {
        UIElementNode scrollContainer = new UIElementNode(
                "android.widget.ScrollView", "scroll_view", "", "",
                "android.widget.ScrollView", "", new Rectangle(0, 0, 1080, 1920),
                false, true, true, true, false, 1
        );
        List<UIElementNode> nodes = Collections.singletonList(scrollContainer);

        // Verification and probing actions must be strictly read-only
        assertFalse(scrollEngine.isScrollEligible("Lanjut", "btn_lanjut", "WAIT_FOR_ELEMENT_PRESENT", nodes));
        assertFalse(scrollEngine.isScrollEligible("Lanjut", "btn_lanjut", "VERIFY_ELEMENT_PRESENT", nodes));
        assertFalse(scrollEngine.isScrollEligible("Lanjut", "btn_lanjut", "IS_ELEMENT_PRESENT", nodes));
        assertFalse(scrollEngine.isScrollEligible("Lanjut", "btn_lanjut", "ASSERT_ELEMENT_PRESENT", nodes));
        assertFalse(scrollEngine.isScrollEligible("Lanjut", "btn_lanjut", "GET_TEXT", nodes));
        assertFalse(scrollEngine.isScrollEligible("Lanjut", "btn_lanjut", "PROBE", nodes));
    }

    @Test
    @DisplayName("Transient overlays and popups MUST NEVER trigger auto-scroll even during TAP action")
    void testScrollEligibilityForOverlaysAndPopups() {
        UIElementNode scrollContainer = new UIElementNode(
                "android.widget.ScrollView", "scroll_view", "", "",
                "android.widget.ScrollView", "", new Rectangle(0, 0, 1080, 1920),
                false, true, true, true, false, 1
        );
        List<UIElementNode> nodes = Collections.singletonList(scrollContainer);

        // Even though action is TAP and screen has a scrollview, popup/overlay target prevents scroll!
        assertFalse(scrollEngine.isScrollEligible("pop up izin notifikasi sama alamat", "Object Repository/pop_izin", "TAP", nodes));
        assertFalse(scrollEngine.isScrollEligible("Izinkan Notifikasi", "btn_allow", "TAP", nodes));
        assertFalse(scrollEngine.isScrollEligible("Tutup Promo Modal", "btn_close_modal", "TAP", nodes));
    }

    @Test
    @DisplayName("Standard buttons in scrollable views ARE eligible for auto-scroll")
    void testScrollEligibilityForStandardFormButton() {
        UIElementNode scrollContainer = new UIElementNode(
                "android.widget.ScrollView", "scroll_view", "", "",
                "android.widget.ScrollView", "", new Rectangle(0, 0, 1080, 1920),
                false, true, true, true, false, 1
        );
        List<UIElementNode> scrollableNodes = Collections.singletonList(scrollContainer);

        // Standard actionable elements in scrollable form can scroll
        assertTrue(scrollEngine.isScrollEligible("Simpan Perubahan", "btn_simpan", "TAP", scrollableNodes));
        assertTrue(scrollEngine.isScrollEligible("Kirim Masukan", "btn_kirim", "TAP", scrollableNodes));

        // When screen is NOT scrollable, it must not scroll
        UIElementNode nonScrollNode = new UIElementNode(
                "android.widget.LinearLayout", "layout", "", "",
                "android.widget.LinearLayout", "", new Rectangle(0, 0, 1080, 1920),
                false, true, true, false, false, 1
        );
        assertFalse(scrollEngine.isScrollEligible("Simpan Perubahan", "btn_simpan", "TAP", Collections.singletonList(nonScrollNode)));
    }

    @Test
    @DisplayName("Scroll recovery can be globally toggled via HealConfig and Locara API")
    void testConfigScrollRecoveryToggle() {
        HealConfig config = new HealConfig();
        assertTrue(config.isScrollRecoveryEnabled());

        config.setScrollRecoveryEnabled(false);
        assertFalse(config.isScrollRecoveryEnabled());

        Locara.setScrollRecoveryEnabled(false);
        assertFalse(Locara.isScrollRecoveryEnabled());

        Locara.setScrollRecoveryEnabled(true);
        assertTrue(Locara.isScrollRecoveryEnabled());
    }

    @Test
    @DisplayName("Should accurately detect horizontal carousels and tab containers")
    void testHorizontalCarouselDetection() {
        UIElementNode horizontalScroll = new UIElementNode(
                "android.widget.HorizontalScrollView", "com.bankmega.msmile:id/scroll_tabs", "", "",
                "android.widget.HorizontalScrollView", "", new Rectangle(0, 100, 1080, 150),
                false, true, true, true, false, 1
        );

        UIElementNode viewPager = new UIElementNode(
                "androidx.viewpager2.widget.ViewPager2", "com.bankmega.msmile:id/carousel_funds", "", "",
                "androidx.viewpager2.widget.ViewPager2", "", new Rectangle(0, 300, 1080, 500),
                false, true, true, true, false, 1
        );

        UIElementNode verticalScroll = new UIElementNode(
                "android.widget.ScrollView", "com.bankmega.msmile:id/scroll_body", "", "",
                "android.widget.ScrollView", "", new Rectangle(0, 0, 1080, 1920),
                false, true, true, true, false, 1
        );

        assertTrue(scrollEngine.hasHorizontalCarousel(Collections.singletonList(horizontalScroll)));
        assertTrue(scrollEngine.hasHorizontalCarousel(Collections.singletonList(viewPager)));
        assertFalse(scrollEngine.hasHorizontalCarousel(Collections.singletonList(verticalScroll)));
    }

    @Test
    @DisplayName("Carousel swipe eligibility should allow TAP on cards but strictly block read-only probes and dialogs")
    void testCarouselEligibilityForCardsAndTabs() {
        UIElementNode horizontalScroll = new UIElementNode(
                "android.widget.HorizontalScrollView", "carousel_products", "", "",
                "android.widget.HorizontalScrollView", "", new Rectangle(0, 100, 1080, 400),
                false, true, true, true, false, 1
        );
        List<UIElementNode> nodes = Collections.singletonList(horizontalScroll);

        // Action TAP on a mutual fund card inside carousel -> eligible!
        assertTrue(scrollEngine.isCarouselEligible("Schroders 90 Plus", "card_schroder", "TAP", nodes));
        assertTrue(scrollEngine.isCarouselEligible("Campuran II", "tab_campuran", "CLICK", nodes));

        // Read-only checks and probes MUST NEVER swipe
        assertFalse(scrollEngine.isCarouselEligible("Schroders 90 Plus", "card_schroder", "WAIT_FOR_ELEMENT_PRESENT", nodes));
        assertFalse(scrollEngine.isCarouselEligible("Schroders 90 Plus", "card_schroder", "VERIFY_ELEMENT_PRESENT", nodes));
        assertFalse(scrollEngine.isCarouselEligible("Schroders 90 Plus", "card_schroder", "PROBE", nodes));

        // Overlay targets MUST NEVER swipe
        assertFalse(scrollEngine.isCarouselEligible("Pop up izin", "pop_izin", "TAP", nodes));
    }

    @AfterEach
    void cleanupManualScrollState() {
        ManualScrollGuard.resetClock();
        ManualScrollGuard.clear();
    }

    @Test
    @DisplayName("Double-scroll guard suppresses auto-scroll immediately after explicit manual scroll")
    void testManualScrollSuppressesDoubleScroll() throws Exception {
        LocaraEngine engine = new LocaraEngine();
        engine.getConfig().setAuditLedgerEnabled(false);
        engine.getConfig().setScrollRecoveryEnabled(true);
        engine.getConfig().setMaxScrollAttempts(2);

        String topXml = "<hierarchy>"
                + "<android.widget.ScrollView scrollable=\"true\" bounds=\"[0,0][1080,1920]\">"
                + "<android.widget.TextView text=\"Header Form\" bounds=\"[50,100][500,180]\"/>"
                + "</android.widget.ScrollView>"
                + "</hierarchy>";
        String bottomXml = "<hierarchy>"
                + "<android.widget.ScrollView scrollable=\"true\" bounds=\"[0,0][1080,1920]\">"
                + "<android.widget.Button resource-id=\"btn_submit\" text=\"Kirim Sekarang\" clickable=\"true\" bounds=\"[50,1700][1030,1840]\"/>"
                + "</android.widget.ScrollView>"
                + "</hierarchy>";

        List<UIElementNode> initialNodes = engine.parsePageSource(topXml);
        HealContext context = HealContext.builder()
                .targetHint("Kirim Sekarang")
                .action("TAP")
                .targetRsPath("Object Repository/btn_submit")
                .build();
        HealResult initialMiss = engine.heal(initialNodes, topXml, context);
        assertFalse(initialMiss.isSuccess());

        AtomicInteger performScrollCount = new AtomicInteger(0);
        ScrollRecoveryEngine spyEngine = new ScrollRecoveryEngine(2) {
            @Override
            public boolean performScrollDown(Object driver) {
                performScrollCount.incrementAndGet();
                return true;
            }
        };

        // 1. User manually scrolled first (e.g. ScrollUtil / Mobile.swipe / Mobile.scrollToText)
        Locara.notifyManualScroll();
        HealResult afterManualScroll = spyEngine.recoverWithScroll(
                engine.getConfig(), engine, context, initialNodes, topXml, initialMiss,
                new Object(), () -> bottomXml, "[Test]"
        );
        assertFalse(afterManualScroll.isSuccess());
        assertEquals(0, performScrollCount.get(), "Auto-scroll must NOT run again right after manual scroll");

        // 2. Once manual scroll state clears, genuine off-screen healing auto-scrolls and recovers cleanly
        Locara.clearManualScrollState();
        HealResult recovered = spyEngine.recoverWithScroll(
                engine.getConfig(), engine, context, initialNodes, topXml, initialMiss,
                new Object(), () -> bottomXml, "[Test]"
        );
        assertTrue(recovered.isSuccess(), "Expected element recovery once manual scroll guard is cleared");
        assertEquals(1, performScrollCount.get(), "Expected exactly 1 autonomous scroll attempt");
    }

    @Test
    @DisplayName("Pre-action ActionWindow preserves manual-scroll decision even after slow timeout before healing")
    void testActionWindowPreservesSuppressionAcrossLongLookupTimeout() {
        AtomicLong fakeNow = new AtomicLong(1_000L);
        ManualScrollGuard.setClockForTests(fakeNow::get);

        HealConfig config = new HealConfig();
        config.setManualScrollGuardEnabled(true);
        config.setManualScrollCooldownMs(5_000L);

        ManualScrollGuard.recordManualScroll();
        fakeNow.set(2_000L); // Action starts 1s after manual scroll

        try (ManualScrollGuard.ActionWindow ignored = ManualScrollGuard.openActionWindow(config)) {
            // Simulate TapKeyword waiting 12 seconds (> 5s cooldown) before throwing StepFailedException
            fakeNow.set(14_000L);
            assertTrue(ManualScrollGuard.isAutoScrollSuppressed(config),
                    "ActionWindow captured at action start must keep auto-scroll suppressed across slow timeout");
        }

        // Next independent action starting at 14s sees 13s elapsed > 5s cooldown -> auto-scroll re-enabled
        assertFalse(ManualScrollGuard.isAutoScrollSuppressed(config),
                "Expired manual scroll cooldown must re-enable auto-scroll for subsequent actions");
    }

    @Test
    @DisplayName("Internal scroll gestures never poison ManualScrollGuard and boundary stagnation halts duplicate scrolls")
    void testInternalScrollGuardAndBoundaryStagnation() throws Exception {
        HealConfig config = new HealConfig();
        ManualScrollGuard.beginInternalScroll();
        try {
            Locara.notifyManualScroll(); // Must be ignored because Locara itself is scrolling
        } finally {
            ManualScrollGuard.endInternalScroll();
        }
        assertFalse(ManualScrollGuard.isAutoScrollSuppressed(config),
                "Internal Locara swipes must not mark user manual scroll");

        LocaraEngine engine = new LocaraEngine();
        engine.getConfig().setAuditLedgerEnabled(false);
        engine.getConfig().setScrollRecoveryEnabled(true);
        engine.getConfig().setMaxScrollAttempts(2);

        String staticBottomXml = "<hierarchy>"
                + "<android.widget.ScrollView scrollable=\"true\" bounds=\"[0,0][1080,1920]\">"
                + "<android.widget.TextView text=\"Footer Unchanged\" bounds=\"[50,100][500,180]\"/>"
                + "</android.widget.ScrollView>"
                + "</hierarchy>";
        List<UIElementNode> initialNodes = engine.parsePageSource(staticBottomXml);
        HealContext context = HealContext.builder().targetHint("Nonexistent CTA").action("TAP").build();
        HealResult initialMiss = engine.heal(initialNodes, staticBottomXml, context);

        AtomicInteger scrollAttempts = new AtomicInteger(0);
        ScrollRecoveryEngine spyEngine = new ScrollRecoveryEngine(2) {
            @Override
            public boolean performScrollDown(Object driver) {
                scrollAttempts.incrementAndGet();
                return true;
            }
        };

        spyEngine.recoverWithScroll(
                engine.getConfig(), engine, context, initialNodes, staticBottomXml, initialMiss,
                new Object(), () -> staticBottomXml, "[Test]"
        );
        assertEquals(1, scrollAttempts.get(),
                "Stagnant unchanged viewport must halt further auto-scroll attempts after attempt #1");
    }

    @Test
    @DisplayName("Post-scroll multi-tap lifecycle keeps second tap protected and resets on completion + ThreadLocal isolation")
    void testPostScrollTapCounterAndThreadIsolation() throws Exception {
        HealConfig config = new HealConfig();
        ManualScrollGuard.recordManualScroll();

        // Action 1 (e.g. checkbox tap after scrollDown) succeeds -> Action 2 (submit button) remains protected
        try (ManualScrollGuard.ActionWindow ignored = ManualScrollGuard.openActionWindow(config)) {
            assertTrue(ManualScrollGuard.isAutoScrollSuppressed(config));
            ManualScrollGuard.markActionTapSucceeded();
            ManualScrollGuard.markActionTapSucceeded(); // Nested mark in same window must only count once
        }
        assertTrue(ManualScrollGuard.isAutoScrollSuppressed(config),
                "Second tap after manual scroll (e.g. submit after consent checkbox) must still suppress auto-scroll");

        // Parallel worker thread must remain completely isolated
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> otherThreadSuppressed = pool.submit(() -> {
                try {
                    return ManualScrollGuard.isAutoScrollSuppressed(config);
                } finally {
                    ManualScrollGuard.clear();
                }
            });
            assertFalse(otherThreadSuppressed.get(), "ThreadLocal scroll state must not leak across worker threads");
        } finally {
            pool.shutdownNow();
        }

        // Action 2 completes -> count reaches DEFAULT_MAX_POST_SCROLL_TAPS (2) -> resets for next screen
        try (ManualScrollGuard.ActionWindow ignored = ManualScrollGuard.openActionWindow(config)) {
            assertTrue(ManualScrollGuard.isAutoScrollSuppressed(config));
            ManualScrollGuard.markActionTapSucceeded();
        }
        assertFalse(ManualScrollGuard.isAutoScrollSuppressed(config),
                "Suppression must reset automatically once post-scroll tap sequence completes");
    }
}
