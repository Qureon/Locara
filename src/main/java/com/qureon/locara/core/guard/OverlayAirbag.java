package com.qureon.locara.core.guard;

import com.qureon.locara.core.driver.ReflectiveAccessUtil;
import com.qureon.locara.core.logging.LocaraLogger;
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
            "com.google.android.permissioncontroller",
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

    private static final List<String> UNAMBIGUOUS_DISMISS_TERMS = Arrays.asList(
            "nanti saja",
            "tutup",
            "close",
            "dismiss",
            "lewati",
            "skip",
            "saya mengerti",
            "mengerti",
            "paham"
    );

    private static final List<String> CONTEXTUAL_ACK_TERMS = Arrays.asList(
            "ok",
            "oke",
            "setuju"
    );

    private static final List<String> MODAL_CONTEXT_SIGNALS = Arrays.asList(
            "dialog",
            "modal",
            "alert",
            "popup",
            "pop_up",
            "promo",
            "notice",
            "pemberitahuan",
            "advisory",
            "overlay",
            "problem",
            "banner",
            "sheet"
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
            LocaraLogger.info("[Locara by Qureon]", "OverlayAirbag scan bypassed: " + t.getMessage());
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
                LocaraLogger.info("[Locara by Qureon]", "🛡️ OverlayAirbag detected OS runtime permission dialog. Auto-allowing via: '" + permButton.getText() + "'");
                return clickNode(driver, permButton);
            }

            // 2. Check for Promotional Modal Overlay with Close Button
            UIElementNode dismissButton = findModalDismissButton(nodes);
            if (dismissButton != null) {
                LocaraLogger.info("[Locara by Qureon]", "🛡️ OverlayAirbag detected blocking modal overlay. Auto-dismissing via: '" + dismissButton.getText() + "'");
                return clickNode(driver, dismissButton);
            }
        } catch (Throwable t) {
            LocaraLogger.info("[Locara by Qureon]", "OverlayAirbag scan bypassed: " + t.getMessage());
        }

        return false;
    }

    public UIElementNode findPermissionAllowButton(List<UIElementNode> nodes) {
        if (nodes == null) return null;
        for (UIElementNode node : nodes) {
            String resId = normalizeText(node.getResourceId());
            String text = normalizeText(node.getText());

            // Explicit deny guard: NEVER click deny/reject buttons (including Android 14+ variants)
            if (resId.contains("permission_deny_button")
                    || resId.contains("permission_deny_and_dont_ask_again_button")
                    || resId.contains("deny")
                    || REJECT_BUTTON_TERMS.contains(text)) {
                continue;
            }

            if (resId.contains("permission_allow_button")
                    || resId.contains("permission_allow_foreground_only_button")
                    || resId.contains("permission_allow_one_time_button")) {
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
        boolean modalContext = hasModalOrDialogContext(nodes);
        for (UIElementNode node : nodes) {
            if (!node.isClickable()) continue;

            String text = normalizeText(node.getText());
            String desc = normalizeText(node.getContentDesc());
            String resId = normalizeText(node.getResourceId());

            if (UNAMBIGUOUS_DISMISS_TERMS.contains(text) || UNAMBIGUOUS_DISMISS_TERMS.contains(desc)) {
                return node;
            }

            if (modalContext && (CONTEXTUAL_ACK_TERMS.contains(text) || CONTEXTUAL_ACK_TERMS.contains(desc))) {
                return node;
            }

            if (resId.contains("btn_close") || resId.contains("btn_dismiss") || desc.equals("close") || desc.equals("tutup")) {
                return node;
            }
        }
        return null;
    }

    private static boolean hasModalOrDialogContext(List<UIElementNode> nodes) {
        boolean hasSignal = false;
        for (UIElementNode n : nodes) {
            if (n.isInputWidget()) {
                return false;
            }
            String combined = normalizeText(n.getTag()) + " " + normalizeText(n.getClassName())
                    + " " + normalizeText(n.getResourceId()) + " " + normalizeText(n.getText())
                    + " " + normalizeText(n.getContentDesc());
            for (String sig : MODAL_CONTEXT_SIGNALS) {
                if (combined.contains(sig)) {
                    hasSignal = true;
                    break;
                }
            }
        }
        return hasSignal;
    }

    private static String normalizeText(String raw) {
        if (raw == null) return "";
        return raw.replace('\u2018', '\'').replace('\u2019', '\'').toLowerCase(Locale.ROOT).trim();
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

            Method xpathMethod = ReflectiveAccessUtil.findAccessibleMethod(byClass, "xpath", String.class);
            Object by = xpathMethod.invoke(null, xpath);

            Method findElementMethod = ReflectiveAccessUtil.findAccessibleMethod(driver.getClass(), "findElement", byClass);
            Object element = findElementMethod.invoke(driver, by);

            Method clickMethod = ReflectiveAccessUtil.findAccessibleMethod(element.getClass(), "click");
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
