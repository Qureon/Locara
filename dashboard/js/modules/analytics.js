/**
 * ==============================================================================
 * ZERO-HEALING DASHBOARD — REGRESSION HEALTH & ANALYTICS MODULE
 * Module healing frequency bars and top volatile locators.
 * ==============================================================================
 */

export const MODULE_ANALYTICS_DATA = [
    { module: "Transfer (Overbooking & BI-FAST)", count: 542, pct: 38 },
    { module: "Payment & Billers (PLN, PDAM, BPJS)", count: 320, pct: 22 },
    { module: "Top-Up e-Wallet (GoPay, OVO, ShopeePay)", count: 264, pct: 19 },
    { module: "QRIS Merchant & Payment", count: 182, pct: 13 },
    { module: "Account, KYC & Biometric Login", count: 88, pct: 6 },
    { module: "Settings & Card Management", count: 32, pct: 2 }
];

export const VOLATILE_LOCATORS_DATA = [
    {
        id: "com.bankmega.msmile:id/btn_next_transfer",
        screen: "Transfer / Konfirmasi",
        changes: 48,
        cause: "ID renamed in Sprint 24 (btn_next -> btn_next_transfer)",
        action: "TAP",
        orTarget: "Object Repository/Transfer/btn_next_transfer.rs"
    },
    {
        id: "com.bankmega.msmile:id/input_security_code",
        screen: "Otorisasi / M-PIN",
        changes: 36,
        cause: "Migration to Jetpack Compose SecureTextField",
        action: "SET_TEXT",
        orTarget: "Object Repository/Transfer/input_security_code.rs"
    },
    {
        id: "com.bankmega.msmile:id/btn_submit_pln",
        screen: "Payment / PLN",
        changes: 29,
        cause: "Dynamic view injection causing index displacement",
        action: "TAP",
        orTarget: "Object Repository/Payment/btn_submit_pln.rs"
    },
    {
        id: "com.bankmega.msmile:id/tv_wallet_title",
        screen: "Top Up / e-Wallet",
        changes: 19,
        cause: "Nested inside Compose Box with dynamic tags",
        action: "TAP",
        orTarget: "Object Repository/TopUp/tv_wallet_title.rs"
    }
];

export function initAnalyticsModule() {
    renderModuleBars();
    renderVolatileLocators();
}

function renderModuleBars() {
    const container = document.getElementById("module-bars-container");
    if (!container) return;

    container.innerHTML = MODULE_ANALYTICS_DATA.map(item => `
        <div class="module-bar-item">
            <div class="module-meta">
                <span class="module-name"><strong>${item.module}</strong></span>
                <span class="module-count">${item.count} perbaikan (${item.pct}%)</span>
            </div>
            <div class="progress-track">
                <div class="progress-fill cyan" style="width: ${item.pct}%;"></div>
            </div>
        </div>
    `).join("");
}

function renderVolatileLocators() {
    const container = document.getElementById("volatile-list-container");
    if (!container) return;

    container.innerHTML = VOLATILE_LOCATORS_DATA.map(v => `
        <div class="volatile-card">
            <div class="v-header">
                <span class="v-id font-mono text-cyan">${escapeHtml(v.id)}</span>
                <span class="v-badge">${v.changes}x Flaky/Changed</span>
            </div>
            <div class="v-body">
                <p class="v-cause"><strong>Root Cause:</strong> ${escapeHtml(v.cause)}</p>
                <p class="v-or font-mono text-sm" style="color: var(--text-muted); margin-top: 4px;">
                    <strong>Target OR:</strong> ${escapeHtml(v.orTarget)}
                </p>
            </div>
        </div>
    `).join("");
}

function escapeHtml(str) {
    if (!str) return "";
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
