/**
 * ==============================================================================
 * LOCARA DASHBOARD — THEME MANAGER (DARK & LIGHT MODE)
 * ==============================================================================
 */

import { renderIcon } from "./icons.js";

const THEME_STORAGE_KEY = "locara-theme";

export function initTheme() {
    const saved = localStorage.getItem(THEME_STORAGE_KEY) || localStorage.getItem("zero-healing-theme");
    const prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    const initialTheme = saved || (prefersDark ? "dark" : "light");

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
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem(THEME_STORAGE_KEY, theme);

    const labelWrap = document.getElementById("theme-label");
    const iconWrap = document.getElementById("theme-icon");

    if (labelWrap) {
        labelWrap.textContent = theme === "dark" ? "Dark Mode" : "Light Mode";
    }

    if (iconWrap) {
        iconWrap.innerHTML = theme === "dark" ? renderIcon("moon") : renderIcon("sun");
    }
}
