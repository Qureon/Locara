package com.qureon.locara.katalon;

import com.qureon.locara.core.model.UIElementNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Evaluates viewport scroll and carousel swipe eligibility while strictly blocking
 * read-only probes, transient overlays, and system permission dialogs.
 */
final class ScrollEligibilityPolicy {

    private static final Set<String> OVERLAY_AND_DIALOG_KEYWORDS = new HashSet<>(Arrays.asList(
            "popup", "pop",
            "modal", "dialog", "alert", "prompt", "overlay", "airbag",
            "izin", "perizinan", "permission", "permissions", "notifikasi", "notification", "notif",
            "lokasi", "location", "alamat", "address", "gps",
            "allow", "izinkan", "deny", "tolak", "block",
            "batal", "cancel", "tutup", "close", "dismiss", "later", "nanti",
            "bottomsheet", "snackbar", "toast", "banner", "promo"
    ));

    private static final String[] OVERLAY_PHRASES = {
            "permissioncontroller", "permission_allow_button", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.google.android.gms.location",
            "pop up", "pop-up", "pop_up", "bottom sheet", "bottom_sheet", "jangan izinkan",
            "system_dialog", "runtime_permission", "grant_permissions"
    };

    private ScrollEligibilityPolicy() {}

    static boolean isOverlayOrDialogTarget(String hint, String targetRsPath) {
        String combined = ((hint != null ? hint : "") + " " + (targetRsPath != null ? targetRsPath : "")).toLowerCase(Locale.ROOT);
        for (String phrase : OVERLAY_PHRASES) {
            if (combined.contains(phrase)) {
                return true;
            }
        }
        String[] tokens = combined.split("[\\s_\\-./]+");
        for (String token : tokens) {
            if (OVERLAY_AND_DIALOG_KEYWORDS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    static boolean isScrollEligible(String hint, String targetRsPath, String action, List<UIElementNode> nodes) {
        return isMutativeNonOverlayTarget(hint, targetRsPath, action) && hasScrollableContainer(nodes);
    }

    static boolean isCarouselEligible(String hint, String targetRsPath, String action, List<UIElementNode> nodes) {
        return isMutativeNonOverlayTarget(hint, targetRsPath, action) && hasHorizontalCarousel(nodes);
    }

    static boolean hasScrollableContainer(List<UIElementNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return false;
        for (UIElementNode node : nodes) {
            if (node.isScrollable()) return true;
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

    static boolean hasHorizontalCarousel(List<UIElementNode> nodes) {
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

    private static boolean isMutativeNonOverlayTarget(String hint, String targetRsPath, String action) {
        if (action == null || action.trim().isEmpty()) {
            return false;
        }
        String act = action.trim().toUpperCase();
        if (act.contains("VERIFY") || act.contains("WAIT") || act.startsWith("IS_")
                || act.contains("ASSERT") || act.contains("PRESENT") || act.contains("VISIBLE")
                || act.contains("PROBE") || act.contains("CHECKPOINT") || act.contains("GET_TEXT")) {
            return false;
        }
        return !isOverlayOrDialogTarget(hint, targetRsPath);
    }
}
