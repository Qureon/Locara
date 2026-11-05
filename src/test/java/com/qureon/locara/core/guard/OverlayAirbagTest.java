package com.qureon.locara.core.guard;

import com.qureon.locara.core.model.UIElementNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OverlayAirbagTest {

    @Test
    @DisplayName("Should detect Android system runtime permission allow button")
    void testDetectPermissionAllowButton() {
        UIElementNode title = new UIElementNode("android.widget.TextView", "com.android.permissioncontroller:id/permission_message", "Allow Mega Mobile to access camera?", "", "android.widget.TextView", "", new Rectangle(100, 300, 800, 100), false, true, true, false, false, 2);
        UIElementNode allowBtn = new UIElementNode("android.widget.Button", "com.android.permissioncontroller:id/permission_allow_foreground_only_button", "While using the app", "", "android.widget.Button", "", new Rectangle(100, 450, 800, 90), true, true, true, false, true, 2);
        UIElementNode denyBtn = new UIElementNode("android.widget.Button", "com.android.permissioncontroller:id/permission_deny_button", "Don't allow", "", "android.widget.Button", "", new Rectangle(100, 560, 800, 90), true, true, true, false, false, 2);

        List<UIElementNode> nodes = Arrays.asList(title, allowBtn, denyBtn);

        OverlayAirbag airbag = new OverlayAirbag();
        UIElementNode detected = airbag.findPermissionAllowButton(nodes);

        assertNotNull(detected);
        assertEquals("While using the app", detected.getText());
    }

    @Test
    @DisplayName("Should strictly reject Deny button even when placed before Allow button in DOM")
    void testNeverSelectDenyButton() {
        UIElementNode title = new UIElementNode("android.widget.TextView", "com.android.permissioncontroller:id/permission_message", "Izinkan Mega Mobile mengakses lokasi?", "", "android.widget.TextView", "", new Rectangle(100, 300, 800, 100), false, true, true, false, false, 2);
        // Notice deny button placed first in list!
        UIElementNode denyBtn = new UIElementNode("android.widget.Button", "com.android.permissioncontroller:id/permission_deny_button", "Jangan Izinkan", "", "android.widget.Button", "", new Rectangle(100, 450, 800, 90), true, true, true, false, false, 2);
        UIElementNode allowBtn = new UIElementNode("android.widget.Button", "com.android.permissioncontroller:id/permission_allow_one_time_button", "Saat aplikasi digunakan", "", "android.widget.Button", "", new Rectangle(100, 560, 800, 90), true, true, true, false, true, 2);

        List<UIElementNode> nodes = Arrays.asList(title, denyBtn, allowBtn);

        OverlayAirbag airbag = new OverlayAirbag();
        UIElementNode detected = airbag.findPermissionAllowButton(nodes);

        assertNotNull(detected);
        assertEquals("Saat aplikasi digunakan", detected.getText(), "Must skip Deny button and select Allow button");

        // When only deny button is present, must return null
        List<UIElementNode> onlyDeny = Arrays.asList(title, denyBtn);
        assertNull(airbag.findPermissionAllowButton(onlyDeny), "Must return null when only Deny is present");
    }

    @Test
    @DisplayName("Should detect unspied promotional modal close button")
    void testDetectModalCloseButton() {
        UIElementNode banner = new UIElementNode("android.widget.ImageView", "img_promo", "", "Promo Bunga Spesial", "android.widget.ImageView", "", new Rectangle(50, 100, 900, 1200), false, true, true, false, false, 2);
        UIElementNode closeBtn = new UIElementNode("android.widget.ImageView", "btn_close_banner", "Tutup", "close", "android.widget.ImageView", "", new Rectangle(850, 120, 80, 80), true, true, true, false, false, 2);

        List<UIElementNode> nodes = Arrays.asList(banner, closeBtn);

        OverlayAirbag airbag = new OverlayAirbag();
        UIElementNode detected = airbag.findModalDismissButton(nodes);

        assertNotNull(detected);
        assertTrue(detected.isClickable());
    }

    @Test
    @DisplayName("Should detect market hours & investment notice dismissal buttons (Saya Mengerti, Paham, OK)")
    void testDetectMarketHoursAdvisoryDismissButton() {
        UIElementNode noticeTitle = new UIElementNode("android.widget.TextView", "tv_title", "Pemberitahuan Jam Bursa", "", "android.widget.TextView", "", new Rectangle(100, 300, 880, 80), false, true, true, false, false, 2);
        UIElementNode noticeBody = new UIElementNode("android.widget.TextView", "tv_desc", "Transaksi setelah pukul 13.00 WIB akan diproses pada hari kerja berikutnya.", "", "android.widget.TextView", "", new Rectangle(100, 390, 880, 200), false, true, true, false, false, 2);
        UIElementNode understandBtn = new UIElementNode("android.widget.Button", "btn_understand", "Saya Mengerti", "", "android.widget.Button", "", new Rectangle(100, 600, 880, 100), true, true, true, false, true, 2);

        List<UIElementNode> nodes = Arrays.asList(noticeTitle, noticeBody, understandBtn);

        OverlayAirbag airbag = new OverlayAirbag();
        UIElementNode detected = airbag.findModalDismissButton(nodes);

        assertNotNull(detected, "OverlayAirbag should detect 'Saya Mengerti' dismissal button");
        assertEquals("Saya Mengerti", detected.getText());
        assertTrue(detected.isClickable());
    }
}
