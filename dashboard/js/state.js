/**
 * ==============================================================================
 * ZERO-HEALING DASHBOARD — CENTRAL STATE MANAGEMENT
 * ==============================================================================
 */

import { APP_CONFIG } from "./config.js";

export const INITIAL_MOCK_EVENTS = [
    {
        id: "EVT-1001",
        timestamp: "2026-10-28 14:48:12",
        screen: "Transfer / Konfirmasi",
        targetHint: "Lanjut",
        action: "TAP",
        confidence: 0.942,
        status: "SUCCESS",
        brokenLocator: "//android.widget.Button[@resource-id='com.bankmega.msmile:id/btn_next']",
        healedLocator: "//android.widget.Button[@resource-id='com.bankmega.msmile:id/btn_next_transfer' and @text='Lanjut']",
        generatedRs: "Object Repository/Transfer/btn_next_transfer.rs",
        module: "Transfer",
        latencyMs: 34,
        vectors: { text: 0.98, id: 0.915, desc: 0.95, clazz: 1.0, proximity: 1.0 },
        guards: { dangerLockout: "PASSED", keyboard: "CLEAR", viewport: "ON-SCREEN" },
        elementText: "Lanjut",
        bounds: "[540,1920][1040,2040]"
    },
    {
        id: "EVT-1002",
        timestamp: "2026-10-28 14:45:03",
        screen: "Transfer / Input M-PIN",
        targetHint: "Input MPIN 6 Digit",
        action: "SET_TEXT",
        confidence: 0.912,
        status: "SUCCESS",
        brokenLocator: "//android.widget.EditText[@resource-id='com.bankmega.msmile:id/et_mpin']",
        healedLocator: "//android.widget.EditText[@resource-id='com.bankmega.msmile:id/input_security_code']",
        generatedRs: "Object Repository/Transfer/input_security_code.rs",
        module: "Transfer",
        latencyMs: 41,
        vectors: { text: 0.85, id: 0.93, desc: 0.92, clazz: 1.0, proximity: 0.95 },
        guards: { dangerLockout: "PASSED", keyboard: "CLEAR", viewport: "ON-SCREEN" },
        elementText: "",
        bounds: "[80,1400][1000,1540]"
    },
    {
        id: "EVT-1003",
        timestamp: "2026-10-28 14:39:51",
        screen: "Payment / PLN Pascabayar",
        targetHint: "Bayar Tagihan",
        action: "TAP",
        confidence: 0.895,
        status: "SCROLLED",
        brokenLocator: "//android.widget.Button[@resource-id='com.bankmega.msmile:id/btn_pay_bill']",
        healedLocator: "//android.widget.Button[@resource-id='com.bankmega.msmile:id/btn_submit_pln' and @text='Bayar Sekarang']",
        generatedRs: "Object Repository/Payment/btn_submit_pln.rs",
        module: "Payment",
        latencyMs: 145,
        vectors: { text: 0.88, id: 0.89, desc: 0.91, clazz: 1.0, proximity: 0.80 },
        guards: { dangerLockout: "PASSED", keyboard: "CLEAR", viewport: "SCROLLED_TO_VIEW" },
        elementText: "Bayar Sekarang",
        bounds: "[60,2100][1020,2240]"
    },
    {
        id: "EVT-1004",
        timestamp: "2026-10-28 14:32:19",
        screen: "Top Up / GoPay",
        targetHint: "Top Up GoPay",
        action: "TAP",
        confidence: 0.965,
        status: "SUCCESS",
        brokenLocator: "//android.widget.TextView[@text='GoPay Customer']",
        healedLocator: "//android.widget.TextView[@resource-id='com.bankmega.msmile:id/tv_wallet_title' and @text='GoPay']",
        generatedRs: "Object Repository/TopUp/tv_wallet_gopay.rs",
        module: "Top-Up",
        latencyMs: 28,
        vectors: { text: 1.0, id: 0.94, desc: 0.96, clazz: 1.0, proximity: 1.0 },
        guards: { dangerLockout: "PASSED", keyboard: "CLEAR", viewport: "ON-SCREEN" },
        elementText: "GoPay",
        bounds: "[120,680][480,760]"
    },
    {
        id: "EVT-1005",
        timestamp: "2026-10-28 14:26:44",
        screen: "Pengaturan / Blokir Kartu",
        targetHint: "Batal Blokir",
        action: "TAP",
        confidence: 0.450,
        status: "LOCKOUT",
        brokenLocator: "//android.widget.Button[@text='Batal']",
        healedLocator: "[BLOCKED BY SAFETY GUARD] Matched destructive element: 'Blokir Permanen'",
        generatedRs: "N/A (Safety Lockout)",
        module: "Settings",
        latencyMs: 12,
        vectors: { text: 0.40, id: 0.35, desc: 0.20, clazz: 1.0, proximity: 0.50 },
        guards: { dangerLockout: "TRIGGERED_LOCKOUT", keyboard: "CLEAR", viewport: "ON-SCREEN" },
        elementText: "Blokir Permanen",
        bounds: "[80,1800][1000,1940]"
    },
    {
        id: "EVT-1006",
        timestamp: "2026-10-28 14:18:22",
        screen: "QRIS / Bayar Merchant",
        targetHint: "Konfirmasi Bayar",
        action: "TAP",
        confidence: 0.958,
        status: "SUCCESS",
        brokenLocator: "//android.widget.Button[@resource-id='com.bankmega.msmile:id/btn_pay_qris']",
        healedLocator: "//android.widget.Button[@resource-id='com.bankmega.msmile:id/btn_confirm_payment' and @text='Konfirmasi']",
        generatedRs: "Object Repository/QRIS/btn_confirm_payment.rs",
        module: "QRIS",
        latencyMs: 31,
        vectors: { text: 0.95, id: 0.94, desc: 0.98, clazz: 1.0, proximity: 1.0 },
        guards: { dangerLockout: "PASSED", keyboard: "CLEAR", viewport: "ON-SCREEN" },
        elementText: "Konfirmasi",
        bounds: "[540,1920][1040,2040]"
    },
    {
        id: "EVT-1007",
        timestamp: "2026-10-28 14:10:05",
        screen: "Login / Biometrik & MPIN",
        targetHint: "Masuk Akun",
        action: "TAP",
        confidence: 0.978,
        status: "SUCCESS",
        brokenLocator: "//android.widget.Button[@resource-id='com.bankmega.msmile:id/btn_login']",
        healedLocator: "//android.widget.Button[@resource-id='com.bankmega.msmile:id/btn_signin' and @text='Masuk']",
        generatedRs: "Object Repository/Login/btn_signin.rs",
        module: "Account/KYC",
        latencyMs: 25,
        vectors: { text: 0.99, id: 0.96, desc: 0.98, clazz: 1.0, proximity: 1.0 },
        guards: { dangerLockout: "PASSED", keyboard: "CLEAR", viewport: "ON-SCREEN" },
        elementText: "Masuk",
        bounds: "[80,1600][1000,1740]"
    }
];

class StateStore {
    constructor() {
        this.events = [...INITIAL_MOCK_EVENTS];
        this.selectedEvent = this.events[0];
        this.filter = "ALL";
        this.search = "";
        this.sortField = "timestamp";
        this.sortOrder = "desc";
        this.currentPage = 1;
        this.pageSize = APP_CONFIG.defaultPageSize;
        this.listeners = [];
    }

    subscribe(listener) {
        this.listeners.push(listener);
        return () => {
            this.listeners = this.listeners.filter(l => l !== listener);
        };
    }

    notify() {
        this.listeners.forEach(fn => fn(this));
    }

    normalizeEvent(e, idx) {
        if (!e) return null;
        const id = e.id || `EVT-${Date.now()}-${idx || 0}`;
        const action = (e.action || "TAP").toUpperCase();
        const hint = e.targetHint || e.hint || "Unnamed Element";
        const screen = e.screen || "DefaultScreen";
        const status = e.status || "SUCCESS";
        const confidence = typeof e.confidence === "number" ? e.confidence : 0.90;
        const brokenLocator = e.brokenLocator || e.origLocator || "";
        const healedLocator = e.healedLocator || e.healedXPath || "";
        const generatedRs = e.generatedRs || e.rsFile || "";
        const module = e.module || (screen.includes("/") ? screen.split("/")[0].trim() : screen);
        const timestamp = e.timestamp ? e.timestamp.replace("T", " ") : new Date().toISOString().replace("T", " ").substring(0, 19);

        return {
            id,
            timestamp,
            screen,
            targetHint: hint,
            action,
            confidence,
            status,
            brokenLocator,
            healedLocator,
            generatedRs,
            module,
            latencyMs: e.latencyMs || 35,
            vectors: e.vectors || { text: 0.95, id: 0.90, desc: 0.90, clazz: 1.0, proximity: 1.0 },
            guards: e.guards || { dangerLockout: status === "LOCKOUT" ? "TRIGGERED_LOCKOUT" : "PASSED", keyboard: "CLEAR", viewport: status === "SCROLLED" ? "SCROLLED_TO_VIEW" : "ON-SCREEN" },
            elementText: e.elementText || hint,
            bounds: e.bounds || "[0,0][0,0]"
        };
    }

    setEvents(events) {
        if (Array.isArray(events) && events.length > 0) {
            this.events = events.map((e, idx) => this.normalizeEvent(e, idx)).filter(Boolean);
        } else {
            this.events = [];
        }
        if (!this.selectedEvent && this.events.length > 0) {
            this.selectedEvent = this.events[0];
        }
        this.currentPage = 1;
        this.notify();
    }

    setSelectedEvent(evt) {
        this.selectedEvent = evt;
        this.notify();
    }

    setFilter(filter) {
        this.filter = filter;
        this.currentPage = 1;
        this.notify();
    }

    setSearch(search) {
        this.search = search.toLowerCase().trim();
        this.currentPage = 1;
        this.notify();
    }

    setSort(field) {
        if (this.sortField === field) {
            this.sortOrder = this.sortOrder === "asc" ? "desc" : "asc";
        } else {
            this.sortField = field;
            this.sortOrder = "desc";
        }
        this.notify();
    }

    setPage(page) {
        this.currentPage = page;
        this.notify();
    }

    getFilteredAndSortedEvents() {
        let result = this.events.filter(e => {
            if (this.filter === "PROBE") {
                const isProbe = (e.action === "VERIFY_PRESENT" || (e.method && e.method.includes("INTENT")) || (e.strategy && e.strategy.includes("INTENT")));
                if (!isProbe) return false;
            } else if (this.filter !== "ALL" && e.status !== this.filter) {
                return false;
            }
            if (this.search) {
                const matchScreen = (e.screen || "").toLowerCase().includes(this.search);
                const matchHint = (e.targetHint || "").toLowerCase().includes(this.search);
                const matchOld = (e.brokenLocator || "").toLowerCase().includes(this.search);
                const matchNew = (e.healedLocator || "").toLowerCase().includes(this.search);
                return matchScreen || matchHint || matchOld || matchNew;
            }
            return true;
        });

        result.sort((a, b) => {
            let valA = a[this.sortField];
            let valB = b[this.sortField];
            if (valA === undefined) return 0;
            if (valB === undefined) return 0;

            if (typeof valA === "string") {
                return this.sortOrder === "asc" ? valA.localeCompare(valB) : valB.localeCompare(valA);
            }
            return this.sortOrder === "asc" ? valA - valB : valB - valA;
        });

        return result;
    }

    getPaginatedEvents() {
        const filtered = this.getFilteredAndSortedEvents();
        const start = (this.currentPage - 1) * this.pageSize;
        return {
            items: filtered.slice(start, start + this.pageSize),
            totalItems: filtered.length,
            totalPages: Math.ceil(filtered.length / this.pageSize) || 1,
            currentPage: this.currentPage
        };
    }
}

export const appState = new StateStore();
