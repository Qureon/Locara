/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — CENTRAL STATE MANAGEMENT
 * ==============================================================================
 */

import { APP_CONFIG } from "./config.js";
import { INITIAL_MOCK_EVENTS } from "./mock-events.js";

export { INITIAL_MOCK_EVENTS };

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
        const intentionTier = e.intentionTier || (
            action.startsWith("IS_") || action.startsWith("ISELEMENT") ? "CONDITIONAL_PROBE" :
            action.startsWith("VERIFY") || action.startsWith("ASSERT") ? "CHECKPOINT" : "INTERACTION"
        );
        const intentionGroup = e.intentionGroup || "";
        const tags = e.tags || "";

        return {
            id,
            timestamp,
            screen,
            targetHint: hint,
            action,
            intentionTier,
            intentionGroup,
            tags,
            confidence,
            status,
            brokenLocator,
            healedLocator,
            generatedRs,
            module,
            latencyMs: e.latencyMs || 35,
            vectors: e.vectors || { text: 0.95, id: 0.90, desc: 0.90, clazz: 1.0, proximity: 1.0 },
            guards: e.guards || {
                dangerLockout: status === "LOCKOUT" ? "TRIGGERED_LOCKOUT" : "PASSED",
                keyboard: "CLEAR",
                viewport: status === "SCROLLED" ? "SCROLLED_TO_VIEW" : "ON-SCREEN"
            },
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
        const result = this.events.filter(e => {
            if (this.filter === "PROBE" || this.filter === "CONDITIONAL_PROBE") {
                const isProbe = e.intentionTier === "CONDITIONAL_PROBE" || (e.action === "VERIFY_PRESENT" || (e.method && e.method.includes("INTENT")) || (e.strategy && e.strategy.includes("INTENT")));
                if (!isProbe) return false;
            } else if (this.filter === "CHECKPOINT") {
                if (e.intentionTier !== "CHECKPOINT" && !e.action.startsWith("VERIFY") && !e.action.startsWith("ASSERT")) return false;
            } else if (this.filter === "INTERACTION") {
                if (e.intentionTier !== "INTERACTION" && !["TAP", "CLICK", "SET_TEXT", "CLEAR_TEXT"].includes(e.action)) return false;
            } else if (this.filter !== "ALL" && e.status !== this.filter) {
                return false;
            }
            if (this.search) {
                const matchScreen = (e.screen || "").toLowerCase().includes(this.search);
                const matchHint = (e.targetHint || "").toLowerCase().includes(this.search);
                const matchOld = (e.brokenLocator || "").toLowerCase().includes(this.search);
                const matchNew = (e.healedLocator || "").toLowerCase().includes(this.search);
                const matchTier = (e.intentionTier || "").toLowerCase().includes(this.search);
                const matchTags = (e.tags || "").toLowerCase().includes(this.search);
                return matchScreen || matchHint || matchOld || matchNew || matchTier || matchTags;
            }
            return true;
        });

        result.sort((a, b) => {
            const valA = a[this.sortField];
            const valB = b[this.sortField];
            if (valA === undefined || valB === undefined) return 0;
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
