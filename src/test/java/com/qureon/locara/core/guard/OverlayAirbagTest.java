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
}
