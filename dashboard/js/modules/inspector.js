/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — VISUAL INSPECTOR MODULE
 * Mobile device simulation, bounding box overlay, diff blocks, and vector bars.
 * ==============================================================================
 */

import { appState } from "../state.js";
import { renderIcon } from "../icons.js";
import { escapeHtml } from "../utils.js";

export function initInspectorModule() {
    appState.subscribe(state => {
        if (state.selectedEvent) {
            renderInspectorDetails(state.selectedEvent);
        } else {
            renderEmptyInspectorState();
        }
    });

    if (appState.selectedEvent) {
        renderInspectorDetails(appState.selectedEvent);
    } else {
        renderEmptyInspectorState();
    }
}

function renderEmptyInspectorState() {
    const oldLoc = document.getElementById("diff-old-locator");
    const newLoc = document.getElementById("diff-new-locator");
    if (oldLoc) oldLoc.textContent = "-";
    if (newLoc) newLoc.textContent = "-";
    ["vec-text", "vec-id", "vec-desc", "vec-class", "vec-prox"].forEach(id => updateVectorRow(id, 0));

    const mockBtn = document.getElementById("target-element-btn");
    const boundingBox = document.getElementById("visual-bounding-box");
    const badgeText = boundingBox ? boundingBox.querySelector(".badge-text") : null;
    if (mockBtn) mockBtn.textContent = "—";
    if (badgeText) badgeText.textContent = "NO EVENT SELECTED";
    if (boundingBox) boundingBox.style.borderColor = "var(--border-color)";

    const container = document.getElementById("guards-status-grid");
    if (container) container.innerHTML = `<p class="text-muted text-sm">Belum ada event audit terpilih.</p>`;
}

function renderInspectorDetails(evt) {
    if (!evt) return;

    // 1. Locator Diff Blocks
    const oldLoc = document.getElementById("diff-old-locator");
    const newLoc = document.getElementById("diff-new-locator");
    if (oldLoc) oldLoc.textContent = evt.brokenLocator || "-";
    if (newLoc) newLoc.textContent = evt.healedLocator || "-";

    // 2. Vector Breakdown Progress Bars
    const v = evt.vectors || { text: 0, id: 0, desc: 0, clazz: 0, proximity: 0 };
    updateVectorRow("vec-text", v.text);
    updateVectorRow("vec-id", v.id);
    updateVectorRow("vec-desc", v.desc);
    updateVectorRow("vec-class", v.clazz);
    updateVectorRow("vec-prox", v.proximity);

    // 3. Mobile Mockup Button & Bounding Box
    const mockBtn = document.getElementById("target-element-btn");
    const boundingBox = document.getElementById("visual-bounding-box");
    const badgeText = boundingBox ? boundingBox.querySelector(".badge-text") : null;

    if (mockBtn) {
        mockBtn.textContent = evt.elementText || evt.targetHint || "Lanjut";
    }

    if (badgeText) {
        const status = String(evt.status || "").toUpperCase();
        if (status === "LOCKOUT") {
            badgeText.textContent = "LOCKOUT: DANGER REJECTED";
            if (boundingBox) boundingBox.style.borderColor = "var(--danger)";
        } else if (status === "SUCCESS" || status === "HEALED" || status === "SCROLLED") {
            badgeText.textContent = `HEALED: ${((evt.confidence || 0) * 100).toFixed(1)}%`;
            if (boundingBox) boundingBox.style.borderColor = "var(--emerald)";
        } else {
            badgeText.textContent = `FAILED: ${status || "REJECTED"}`;
            if (boundingBox) boundingBox.style.borderColor = "var(--danger)";
        }
    }

    // 4. Guards Status Grid
    renderGuardsStatus(evt.guards);
}

function updateVectorRow(elementId, score) {
    const el = document.getElementById(elementId);
    if (!el) return;
    const pct = Math.min(100, Math.max(0, (score * 100))).toFixed(1);
    el.textContent = `${pct}%`;
    const bar = el.closest(".vector-bar-row")?.querySelector(".progress-fill");
    if (bar) {
        bar.style.width = `${pct}%`;
    }
}

function renderGuardsStatus(guards) {
    const container = document.getElementById("guards-status-grid");
    if (!container) return;

    const g = guards || { dangerLockout: "PASSED", keyboard: "CLEAR", viewport: "ON-SCREEN" };
    const dangerStr = String(g.dangerLockout || "PASSED");
    const keyboardStr = String(g.keyboard || "CLEAR");
    const viewportStr = String(g.viewport || "ON-SCREEN");
    const isLockout = dangerStr.includes("LOCKOUT");

    container.innerHTML = `
        <div class="guard-pill ${isLockout ? 'danger' : 'safe'}">
            <span class="btn-icon-wrap">${renderIcon(isLockout ? "alertTriangle" : "shield")}</span>
            <span>Danger Lockout: <strong>${escapeHtml(dangerStr)}</strong></span>
        </div>
        <div class="guard-pill safe">
            <span class="btn-icon-wrap">${renderIcon("checkCircle")}</span>
            <span>Keyboard Guard: <strong>${escapeHtml(keyboardStr)}</strong></span>
        </div>
        <div class="guard-pill safe">
            <span class="btn-icon-wrap">${renderIcon("activity")}</span>
            <span>Viewport: <strong>${escapeHtml(viewportStr)}</strong></span>
        </div>
    `;
}
