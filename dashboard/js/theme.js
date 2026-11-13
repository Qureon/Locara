/**
 * ==============================================================================
 * LOCARA DASHBOARD — THEME MANAGER (DARK & LIGHT MODE)
 * ==============================================================================
 */

import { renderIcon } from "./icons.js";

const THEME_STORAGE_KEY = "locara-theme";
const LEGACY_THEME_STORAGE_KEY = "zero-healing-theme";

function normalizeTheme(candidate, prefersDark) {
    if (candidate === "light" || candidate === "dark") {
        return candidate;
    }
    return prefersDark ? "dark" : "light";
}

export function initTheme() {
    const legacySaved = localStorage.getItem(LEGACY_THEME_STORAGE_KEY);
    const saved = localStorage.getItem(THEME_STORAGE_KEY) || legacySaved;
    if (legacySaved !== null) {
        localStorage.removeItem(LEGACY_THEME_STORAGE_KEY);
    }
    const prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    const initialTheme = normalizeTheme(saved, prefersDark);

    applyTheme(initialTheme);

    const toggleBtn = document.getElementById("btn-theme-toggle");
    if (toggleBtn) {
        toggleBtn.addEventListener("click", () => {
            const current = document.documentElement.getAttribute("data-theme") || "dark";
            const next = current === "dark" ? "light" : "dark";
            applyTheme(next);
        });
    }
}

export function applyTheme(theme) {
    const normalized = theme === "light" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", normalized);
    localStorage.setItem(THEME_STORAGE_KEY, normalized);

    const labelWrap = document.getElementById("theme-label");
    const iconWrap = document.getElementById("theme-icon");

    if (labelWrap) {
        labelWrap.textContent = normalized === "dark" ? "Dark Mode" : "Light Mode";
    }

    if (iconWrap) {
        iconWrap.innerHTML = normalized === "dark" ? renderIcon("moon") : renderIcon("sun");
    }
}
