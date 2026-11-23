/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — REGRESSION HEALTH & ANALYTICS MODULE
 * Module healing frequency bars and top volatile locators.
 * ==============================================================================
 */

import { appState } from "../state.js";
import { escapeHtml } from "../utils.js";

export function computeModuleAnalytics(events) {
    if (!Array.isArray(events) || events.length === 0) return [];
    const counts = new Map();
    for (const evt of events) {
        const key = (evt.module || evt.screen || "DefaultScreen").trim() || "DefaultScreen";
        counts.set(key, (counts.get(key) || 0) + 1);
    }
    const total = events.length;
    return Array.from(counts.entries())
        .sort((a, b) => b[1] - a[1])
        .slice(0, 8)
        .map(([module, count]) => ({
            module,
            count,
            pct: Math.round((count / total) * 100)
        }));
}

export function computeVolatileLocators(events) {
    if (!Array.isArray(events) || events.length === 0) return [];
    const grouped = new Map();
    for (const evt of events) {
        const key = (evt.brokenLocator || evt.targetHint || "Unnamed Locator").trim();
        const existing = grouped.get(key);
        if (existing) {
            existing.changes += 1;
        } else {
            grouped.set(key, {
                id: key,
                screen: evt.screen || "DefaultScreen",
                changes: 1,
                cause: `${evt.action || "TAP"} (${evt.status || "UNKNOWN"}) on ${evt.screen || "DefaultScreen"}`,
                action: evt.action || "TAP",
                orTarget: evt.generatedRs || "In-Memory Patch"
            });
        }
    }
    return Array.from(grouped.values())
        .sort((a, b) => b.changes - a.changes)
        .slice(0, 6);
}

export function initAnalyticsModule() {
    appState.subscribe(() => {
        renderModuleBars();
        renderVolatileLocators();
    });
    renderModuleBars();
    renderVolatileLocators();
}

function renderModuleBars() {
    const container = document.getElementById("module-bars-container");
    if (!container) return;

    const data = computeModuleAnalytics(appState.events);
    if (data.length === 0) {
        container.innerHTML = `<p class="text-muted text-sm">Belum ada event audit untuk dianalisis.</p>`;
        return;
    }

    container.innerHTML = data.map(item => {
        const safePct = Math.max(0, Math.min(100, Number(item.pct) || 0));
        return `
        <div class="module-bar-item">
            <div class="module-meta">
                <span class="module-name"><strong>${escapeHtml(item.module)}</strong></span>
                <span class="module-count">${escapeHtml(String(item.count))} event (${safePct}%)</span>
            </div>
            <div class="progress-track">
                <div class="progress-fill cyan" style="width: ${safePct}%;"></div>
            </div>
        </div>
    `;
    }).join("");
}

function renderVolatileLocators() {
    const container = document.getElementById("volatile-list-container");
    if (!container) return;

    const items = computeVolatileLocators(appState.events);
    if (items.length === 0) {
        container.innerHTML = `<p class="text-muted text-sm">Belum ada locator volatil yang tercatat.</p>`;
        return;
    }

    container.innerHTML = items.map(v => `
        <div class="volatile-card">
            <div class="v-header">
                <span class="v-id font-mono text-cyan">${escapeHtml(v.id)}</span>
                <span class="v-badge">${escapeHtml(String(v.changes))}x Terdeteksi</span>
            </div>
            <div class="v-body">
                <p class="v-cause"><strong>Konteks:</strong> ${escapeHtml(v.cause)}</p>
                <p class="v-or font-mono text-sm" style="color: var(--text-muted); margin-top: 4px;">
                    <strong>Target OR:</strong> ${escapeHtml(v.orTarget)}
                </p>
            </div>
        </div>
    `).join("");
}
