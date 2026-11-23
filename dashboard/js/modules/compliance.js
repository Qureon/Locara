/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — UAT COMPLIANCE & SIGN-OFF MODULE
 * Formal audit certificate verification and printable document generator.
 * ==============================================================================
 */

import { APP_CONFIG } from "../config.js";
import { appState } from "../state.js";

const SUCCESS_STATUSES = new Set(["SUCCESS", "HEALED", "SCROLLED"]);
let complianceRenderSeq = 0;

export function initComplianceModule() {
    const btnPrint = document.getElementById("btn-print-certificate");
    if (btnPrint) {
        btnPrint.addEventListener("click", () => {
            window.print();
        });
    }

    const btnExportReport = document.getElementById("btn-export-html-report");
    if (btnExportReport) {
        btnExportReport.addEventListener("click", () => {
            window.open("/api/report", "_blank");
        });
    }

    appState.subscribe(() => {
        renderComplianceSummary();
    });
    renderComplianceSummary();
}

async function computeAuditDigest(events) {
    const canonical = JSON.stringify(
        events.map(e => [e.id, e.timestamp, e.screen, e.targetHint, e.status, e.confidence])
    );
    if (typeof crypto !== "undefined" && crypto.subtle && typeof TextEncoder !== "undefined") {
        try {
            const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(canonical));
            const hex = Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, "0")).join("");
            return `SHA-256: ${hex.slice(0, 12)}...${hex.slice(-8)}`;
        } catch (_) {}
    }
    let hash = 2166136261;
    for (let i = 0; i < canonical.length; i++) {
        hash ^= canonical.charCodeAt(i);
        hash = Math.imul(hash, 16777619);
    }
    return `DIGEST: ${(hash >>> 0).toString(16).padStart(8, "0")}`;
}

async function renderComplianceSummary() {
    const seq = ++complianceRenderSeq;
    const events = Array.isArray(appState.events) ? appState.events : [];
    const total = events.length;
    const healedCount = events.filter(e => SUCCESS_STATUSES.has(String(e.status || "").toUpperCase())).length;
    const lockouts = events.filter(e => String(e.status || "").toUpperCase() === "LOCKOUT").length;
    const safetyRate = total > 0 ? `${(((healedCount + lockouts) / total) * 100).toFixed(1)}%` : "0.0%";

    const elVer = document.getElementById("comp-engine-version");
    const elHash = document.getElementById("comp-integrity-hash");
    const elSafety = document.getElementById("comp-safety-rate");
    const elHealed = document.getElementById("comp-healed-count");
    const elLockouts = document.getElementById("comp-lockout-count");

    if (elVer) elVer.textContent = `Locara Core ${APP_CONFIG.semver}`;
    if (elSafety) elSafety.textContent = safetyRate;
    if (elHealed) elHealed.textContent = healedCount.toLocaleString();
    if (elLockouts) elLockouts.textContent = lockouts.toLocaleString();
    if (elHash) {
        const digest = total > 0 ? await computeAuditDigest(events) : "N/A (No Events)";
        if (seq === complianceRenderSeq) {
            elHash.textContent = digest;
        }
    }
}
