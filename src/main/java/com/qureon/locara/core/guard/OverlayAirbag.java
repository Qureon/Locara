package com.qureon.locara.core.guard;

import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.core.parser.PageSourceParser;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 🛡️ OverlayAirbag — Autonomous OS Permission & Modal Overlay Neutralizer.
 *
 * Detects and automatically neutralizes unexpected blocking overlays:
 * 1. Android/iOS System Runtime Permissions (Camera, Storage, Location, Notifications).
 * 2. Unspied promotional modal dialogs that obscure target application elements.
 */
public class OverlayAirbag {

    private static final List<String> PERMISSION_PACKAGES = Arrays.asList(
            "com.android.permissioncontroller",
            "com.google.android.packageinstaller"
    );

    private static final List<String> ALLOW_BUTTON_TERMS = Arrays.asList(
            "while using the app",
            "saat aplikasi digunakan",
            "only this time",
            "hanya kali ini",
            "allow",
            "izinkan"
    );

    private static final List<String> REJECT_BUTTON_TERMS = Arrays.asList(
            "don't allow",
            "dont allow",
            "deny",
            "jangan izinkan",
            "tolak",
            "tidak pernah",
            "never"
    );

    private static final List<String> DISMISS_MODAL_TERMS = Arrays.asList(
            "nanti saja",
            "tutup",
            "close",
            "dismiss",
            "lewati",
            "skip",
            "saya mengerti",
            "mengerti",
            "paham",
            "ok",
            "oke",
            "setuju"
    );

    /**
     * Inspects active page source and driver context.
     * If a blocking system permission or modal dialog is detected, dismisses it and returns true.
     */
    public boolean dismissBlockingOverlay(Object driver, String xmlPageSource) {
        if (driver == null || xmlPageSource == null || xmlPageSource.isEmpty()) {
            return false;
        }

        try {
            PageSourceParser parser = new PageSourceParser();
            List<UIElementNode> nodes = parser.parse(xmlPageSource);
            return dismissBlockingOverlay(driver, nodes);
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] OverlayAirbag scan bypassed: " + t.getMessage());
            return false;
        }
    }

    /**
     * Inspects pre-parsed UI hierarchy nodes and dismisses any blocking OS permission or modal overlay.
     */
    public boolean dismissBlockingOverlay(Object driver, List<UIElementNode> nodes) {
        if (driver == null || nodes == null || nodes.isEmpty()) {
            return false;
        }

        try {
            // 1. Check for OS System Permission Dialog
            UIElementNode permButton = findPermissionAllowButton(nodes);
            if (permButton != null) {
                System.out.println("[Locara by Qureon] 🛡️ OverlayAirbag detected OS runtime permission dialog. Auto-allowing via: '" + permButton.getText() + "'");
                return clickNode(driver, permButton);
            }

            // 2. Check for Promotional Modal Overlay with Close Button
            UIElementNode dismissButton = findModalDismissButton(nodes);
            if (dismissButton != null) {
                System.out.println("[Locara by Qureon] 🛡️ OverlayAirbag detected blocking modal overlay. Auto-dismissing via: '" + dismissButton.getText() + "'");
                return clickNode(driver, dismissButton);
            }
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] OverlayAirbag scan bypassed: " + t.getMessage());
        }

        return false;
    }

    public UIElementNode findPermissionAllowButton(List<UIElementNode> nodes) {
        if (nodes == null) return null;
        for (UIElementNode node : nodes) {
            String resId = node.getResourceId().toLowerCase();
            String text = node.getText().toLowerCase().trim();

            // Explicit deny guard: NEVER click deny/reject buttons
            if (resId.contains("permission_deny_button") || resId.contains("deny") || REJECT_BUTTON_TERMS.contains(text)) {
                continue;
            }

            if (resId.contains("permission_allow_button") || resId.contains("permission_allow_foreground_only_button") || resId.contains("permission_allow_one_time_button")) {
                return node;
            }

            for (String pkg : PERMISSION_PACKAGES) {
                if (resId.startsWith(pkg) && ALLOW_BUTTON_TERMS.contains(text)) {
                    return node;
                }
            }

            if (node.isClickable() && ALLOW_BUTTON_TERMS.contains(text)) {
                return node;
            }
        }
        return null;
    }

    public UIElementNode findModalDismissButton(List<UIElementNode> nodes) {
        if (nodes == null) return null;
        for (UIElementNode node : nodes) {
            if (!node.isClickable()) continue;

            String text = node.getText().toLowerCase().trim();
            String desc = node.getContentDesc().toLowerCase().trim();
            String resId = node.getResourceId().toLowerCase();

            if (DISMISS_MODAL_TERMS.contains(text) || DISMISS_MODAL_TERMS.contains(desc)) {
                return node;
            }

            if (resId.contains("btn_close") || resId.contains("btn_dismiss") || desc.equals("close") || desc.equals("tutup")) {
                return node;
            }
        }
        return null;
    }

    private boolean clickNode(Object driver, UIElementNode node) {
        try {
            Class<?> byClass = Class.forName("org.openqa.selenium.By");
            String xpath;
            if (node.hasResourceId()) {
                xpath = "//*[@resource-id=" + com.qureon.locara.core.generator.DynamicXPathGenerator.toXPathLiteral(node.getResourceId()) + "]";
            } else if (node.hasText()) {
                xpath = "//*[@text=" + com.qureon.locara.core.generator.DynamicXPathGenerator.toXPathLiteral(node.getText()) + "]";
            } else if (node.hasContentDesc()) {
                xpath = "//*[@content-desc=" + com.qureon.locara.core.generator.DynamicXPathGenerator.toXPathLiteral(node.getContentDesc()) + "]";
            } else {
                return false;
            }

            Method xpathMethod = byClass.getMethod("xpath", String.class);
            Object by = xpathMethod.invoke(null, xpath);

            Method findElementMethod = driver.getClass().getMethod("findElement", byClass);
            Object element = findElementMethod.invoke(driver, by);

            Method clickMethod = element.getClass().getMethod("click");
            clickMethod.invoke(element);
            Thread.sleep(600);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
