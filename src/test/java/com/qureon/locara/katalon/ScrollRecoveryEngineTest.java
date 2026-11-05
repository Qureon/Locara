package com.qureon.locara.katalon;

import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.UIElementNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

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
}
