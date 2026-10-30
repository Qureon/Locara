/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — MASTER APPLICATION CONTROLLER
 * Powered by Qureon · Modular ES Architecture Entry Point
 * ==============================================================================
 */

import { initTheme } from "./theme.js";
import { renderIcon } from "./icons.js";
import { fetchAuditEvents, fetchEngineHealth } from "./api.js";
import { appState } from "./state.js";
import { initTelemetryModule } from "./modules/telemetry.js";
import { initInspectorModule } from "./modules/inspector.js";
import { initAnalyticsModule } from "./modules/analytics.js";
import { initPlaygroundModule } from "./modules/playground.js";
import { initComplianceModule } from "./modules/compliance.js";
import { initSettingsModule } from "./modules/settings.js";
import { initFormInspectorModule } from "./modules/formInspector.js";

// Global forensic modal viewer
window.viewEventForensics = function(eventId) {
    const evt = appState.events.find(x => x.id === eventId);
    if (!evt) return;

    const modal = document.getElementById("detail-modal");
    const title = document.getElementById("modal-title");
    const body = document.getElementById("modal-body");

    if (!modal || !body) return;

    const actionStr = (evt.action || "TAP").toUpperCase();
    const targetHintStr = evt.targetHint || evt.hint || "-";
    const rsPath = evt.generatedRs || evt.rsFile || "-";
    const oldLoc = evt.brokenLocator || evt.origLocator || "-";
    const newLoc = evt.healedLocator || evt.healedXPath || "-";

    title.textContent = `Forensic Trace: ${evt.id}`;
    body.innerHTML = `
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
            <div class="m-card" style="background: var(--bg-surface); padding: 14px; border-radius: var(--radius-md); border: 1px solid var(--border-color);">
                <h4 style="font-size: 0.88rem; margin-bottom: 8px;">Konteks Eksekusi</h4>
                <p class="text-sm"><strong>Waktu:</strong> ${evt.timestamp}</p>
                <p class="text-sm"><strong>Screen:</strong> ${escapeHtml(evt.screen || 'DefaultScreen')}</p>
                <p class="text-sm"><strong>Aksi:</strong> <span class="action-tag ${actionStr.toLowerCase()}">${actionStr}</span></p>
                <p class="text-sm"><strong>Target Hint:</strong> "${escapeHtml(targetHintStr)}"</p>
                <p class="text-sm"><strong>Latency:</strong> ${evt.latencyMs || 0} ms</p>
            </div>

            <div class="m-card" style="background: var(--bg-surface); padding: 14px; border-radius: var(--radius-md); border: 1px solid var(--border-color);">
                <h4 style="font-size: 0.88rem; margin-bottom: 8px;">Hasil Self-Healing</h4>
                <p class="text-sm"><strong>Confidence:</strong> ${((evt.confidence || 0) * 100).toFixed(1)}%</p>
                <p class="text-sm"><strong>Status:</strong> ${evt.status || 'SUCCESS'}</p>
                <p class="text-sm"><strong>Danger Guard:</strong> ${evt.guards ? evt.guards.dangerLockout : 'PASSED'}</p>
                <p class="text-sm"><strong>Object Repository:</strong></p>
                <code class="text-xs font-mono" style="color: var(--cyan); word-break: break-all;">${escapeHtml(rsPath)}</code>
            </div>
        </div>

        <div style="margin-top: 12px; display: flex; flex-direction: column; gap: 8px;">
            <h4 style="font-size: 0.88rem;">Locator Forensic Diff</h4>
            <div class="diff-block broken">
                <span class="diff-title">Broken Original Locator:</span>
                <code>${escapeHtml(oldLoc)}</code>
            </div>
            <div class="diff-block healed">
                <span class="diff-title">Synthesized Healed XPath:</span>
                <code>${escapeHtml(newLoc)}</code>
            </div>
        </div>
    `;

    modal.classList.remove("hidden");
};

document.addEventListener("DOMContentLoaded", async () => {
    // 1. Initialize Theme (Dark / Light)
    initTheme();

    // 2. Inject Vector SVG Icons across placeholders
    injectStaticIcons();

    // 3. Initialize Navigation & Mobile Drawer
    initNavigation();
    initMobileDrawer();
    initModalControls();

    // 4. Initialize Feature Modules
    initTelemetryModule();
    initInspectorModule();
    initAnalyticsModule();
    initPlaygroundModule();
    initComplianceModule();
    initSettingsModule();
    initFormInspectorModule();

    // 5. Connect to Live Server Telemetry
    await connectLiveEngine();
});

function injectStaticIcons() {
    document.querySelectorAll("[data-icon]").forEach(el => {
        const iconName = el.dataset.icon;
        el.innerHTML = renderIcon(iconName);
    });
}

function initNavigation() {
    const navItems = document.querySelectorAll(".sidebar-nav .nav-item");
    const tabPanes = document.querySelectorAll(".tab-pane");
    const heading = document.getElementById("page-heading");
    const subtitle = document.getElementById("page-subtitle");

    const titles = {
        "telemetry": {
            title: "Autonomous Self-Healing Mission Control",
            sub: "Pemantauan real-time in-flight self-healing, ledgers, dan recovery locator Revamp M-Smile."
        },
        "visual-diff": {
            title: "Visual Bounding Box & Deep Inspector",
            sub: "Simulasi koordinat elemen pada layar mobile beserta analisis breakdown 5-vektor kecocokan."
        },
        "analytics": {
            title: "Regression Health & Volatility Analytics",
            sub: "Distribusi perbaikan otonom per modul transaksi Bank Mega dan deteksi elemen UI paling rapuh."
        },
        "playground": {
            title: "Interactive XML Reconnaissance Sandbox",
            sub: "Uji pencocokan XML hierarki Appium dan ekspor instan spesifikasi Object Repository (.rs)."
        },
        "form-inspector": {
            title: "Universal Spatial & Form Relationship Inspector",
            sub: "Analisis visual relasi spasial 2D (Left-to-Right & Top-to-Bottom), deteksi batas section, dan ekstraksi key-value otonom."
        },
        "compliance": {
            title: "UAT Compliance & Audit Sign-Off",
            sub: "Dokumentasi verifikasi resmi integritas pengujian otomasi regresi bersertifikasi QA Bank Mega."
        },
        "settings": {
            title: "Engine Configuration & Tuning",
            sub: "Pengaturan bobot matematis heuristik, threshold sensitivitas, dan daftar proteksi tombol bahaya."
        }
    };

    navItems.forEach(item => {
        item.addEventListener("click", () => {
            const tabKey = item.dataset.tab;

            navItems.forEach(n => n.classList.remove("active"));
            item.classList.add("active");

            tabPanes.forEach(p => p.classList.remove("active"));
            const targetPane = document.getElementById(`pane-${tabKey}`);
            if (targetPane) targetPane.classList.add("active");

            if (titles[tabKey]) {
                if (heading) heading.textContent = titles[tabKey].title;
                if (subtitle) subtitle.textContent = titles[tabKey].sub;
            }

            // Close mobile drawer on navigation
            const sidebar = document.querySelector(".sidebar");
            if (sidebar) sidebar.classList.remove("open");
        });
    });
}

function initMobileDrawer() {
    const toggleBtn = document.getElementById("btn-mobile-menu");
    const sidebar = document.querySelector(".sidebar");
    const backdrop = document.getElementById("sidebar-backdrop");

    if (toggleBtn && sidebar) {
        toggleBtn.addEventListener("click", () => {
            sidebar.classList.toggle("open");
        });
    }

    if (backdrop && sidebar) {
        backdrop.addEventListener("click", () => {
            sidebar.classList.remove("open");
        });
    }
}

function initModalControls() {
    const modal = document.getElementById("detail-modal");
    const closeBtn = document.getElementById("modal-close-btn");

    if (closeBtn && modal) {
        closeBtn.addEventListener("click", () => modal.classList.add("hidden"));
        modal.addEventListener("click", (e) => {
            if (e.target === modal) modal.classList.add("hidden");
        });
    }
}

async function connectLiveEngine() {
    const connPill = document.getElementById("connection-pill");
    const refreshBtn = document.getElementById("btn-refresh");

    async function check() {
        if (refreshBtn) refreshBtn.classList.add("spin");

        const health = await fetchEngineHealth();
        const liveEvents = await fetchAuditEvents();

        if (refreshBtn) {
            setTimeout(() => refreshBtn.classList.remove("spin"), 500);
        }

        if (connPill) {
            if (health.status === "ONLINE") {
                connPill.innerHTML = `
                    <span class="live-pulse"></span>
                    <span class="pill-text">${health.target}: ONLINE</span>
                `;
                connPill.style.borderColor = "var(--emerald)";
            } else {
                connPill.innerHTML = `
                    <span class="live-pulse" style="background: var(--cyan);"></span>
                    <span class="pill-text">STANDALONE MODE</span>
                `;
            }
        }

        if (liveEvents && liveEvents.length > 0) {
            appState.setEvents(liveEvents);
        }
    }

    if (refreshBtn) {
        refreshBtn.addEventListener("click", check);
    }

    await check();
}

function escapeHtml(str) {
    if (!str) return "";
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
