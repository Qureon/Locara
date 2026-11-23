/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — TELEMETRY MODULE
 * Table rendering, sorting, pagination, search, and audit export.
 * ==============================================================================
 */

import { appState } from "../state.js";
import { renderIcon } from "../icons.js";
import { escapeHtml } from "../utils.js";

export function initTelemetryModule() {
    initSearchAndFilters();
    initTableSorting();
    initPaginationControls();
    initExportButton();

    appState.subscribe(() => {
        renderTelemetryTable();
        updateKpiMetrics();
    });

    renderTelemetryTable();
    updateKpiMetrics();
}

function initSearchAndFilters() {
    const searchInput = document.getElementById("log-search");
    if (searchInput) {
        searchInput.addEventListener("input", (e) => {
            appState.setSearch(e.target.value);
        });
    }

    const chips = document.querySelectorAll(".filter-chips .chip");
    chips.forEach(chip => {
        chip.addEventListener("click", () => {
            chips.forEach(c => c.classList.remove("active"));
            chip.classList.add("active");
            appState.setFilter(chip.dataset.filter);
        });
    });
}

function initTableSorting() {
    const headers = document.querySelectorAll(".audit-table th[data-sort]");
    headers.forEach(th => {
        th.addEventListener("click", () => {
            const field = th.dataset.sort;
            appState.setSort(field);
            updateSortIcons(th, appState.sortOrder);
        });
    });
}

function updateSortIcons(activeTh, order) {
    document.querySelectorAll(".audit-table th .th-sort-icon").forEach(icon => {
        icon.innerHTML = "";
    });
    const targetIcon = activeTh.querySelector(".th-sort-icon");
    if (targetIcon) {
        targetIcon.innerHTML = order === "asc" ? renderIcon("chevronUp") : renderIcon("chevronDown");
    }
}

function initPaginationControls() {
    const btnPrev = document.getElementById("btn-page-prev");
    const btnNext = document.getElementById("btn-page-next");

    if (btnPrev) {
        btnPrev.addEventListener("click", () => {
            if (appState.currentPage > 1) {
                appState.setPage(appState.currentPage - 1);
            }
        });
    }

    if (btnNext) {
        btnNext.addEventListener("click", () => {
            const { totalPages } = appState.getPaginatedEvents();
            if (appState.currentPage < totalPages) {
                appState.setPage(appState.currentPage + 1);
            }
        });
    }
}

function initExportButton() {
    const btnExport = document.getElementById("btn-export-audit");
    if (btnExport) {
        btnExport.addEventListener("click", () => {
            const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(appState.events, null, 2));
            const a = document.createElement("a");
            a.setAttribute("href", dataStr);
            a.setAttribute("download", `locara_audit_${Date.now()}.json`);
            document.body.appendChild(a);
            a.click();
            a.remove();
        });
    }
}

export function renderTelemetryTable() {
    const tbody = document.getElementById("audit-table-body");
    if (!tbody) return;

    const { items, totalItems, totalPages, currentPage } = appState.getPaginatedEvents();

    if (items.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" style="text-align: center; padding: 48px; color: var(--text-muted);">
                    <div style="display:flex; flex-direction:column; align-items:center; gap:8px;">
                        <span style="width:24px; height:24px; color:var(--text-muted);">${renderIcon("info")}</span>
                        <span>Tidak ada event yang sesuai dengan filter atau kata kunci pencarian.</span>
                    </div>
                </td>
            </tr>
        `;
        renderPaginationBar(0, 0, 0);
        return;
    }

    tbody.innerHTML = items.map(evt => {
        const confPct = ((evt.confidence || 0) * 100).toFixed(1);
        let confLevel = "high";
        if (evt.confidence < 0.70) confLevel = "low";
        else if (evt.confidence < 0.90) confLevel = "medium";

        const rawStatus = String(evt.status || "UNKNOWN").toUpperCase();
        let statusClass = "lockout";
        let statusLabel = rawStatus;
        if (rawStatus === "SUCCESS" || rawStatus === "HEALED") {
            statusClass = "success";
            statusLabel = rawStatus;
        } else if (rawStatus === "SCROLLED") {
            statusClass = "scrolled";
            statusLabel = "AUTO-SCROLLED";
        } else if (rawStatus === "LOCKOUT") {
            statusClass = "lockout";
            statusLabel = "LOCKOUT";
        }

        const actionStr = (evt.action || "TAP").toUpperCase();
        const tier = (evt.intentionTier || "INTERACTION").toUpperCase();
        let tierPill = `<span class="badge" style="font-size:0.68rem; padding:2px 6px; background:rgba(0,229,255,0.12); color:var(--cyan); border:1px solid rgba(0,229,255,0.25); border-radius:4px; margin-right:4px;" title="User Interaction">INT</span>`;
        if (tier === "CHECKPOINT") {
            tierPill = `<span class="badge" style="font-size:0.68rem; padding:2px 6px; background:rgba(16,185,129,0.12); color:var(--emerald); border:1px solid rgba(16,185,129,0.25); border-radius:4px; margin-right:4px;" title="Assertion Checkpoint (Katalon 11.2+ assertElement*)">CHK</span>`;
        } else if (tier === "CONDITIONAL_PROBE") {
            tierPill = `<span class="badge" style="font-size:0.68rem; padding:2px 6px; background:rgba(139,92,246,0.12); color:var(--violet); border:1px solid rgba(139,92,246,0.25); border-radius:4px; margin-right:4px;" title="Conditional Status Probe (Katalon 11.2+ isElement*)">PRB</span>`;
        }

        const rsPath = evt.generatedRs || evt.rsFile || "";
        const rsShortName = rsPath ? rsPath.split(/[/\\]/).pop() : "-";
        const targetHintStr = evt.targetHint || evt.hint || "-";
        const timeDisplay = evt.timestamp ? ((evt.timestamp.includes(" ") ? evt.timestamp.split(" ")[1] : evt.timestamp.replace("T", " "))) : "-";

        const safeId = escapeHtml(String(evt.id || ""));

        return `
            <tr class="audit-row" data-id="${safeId}">
                <td class="font-mono text-muted text-sm">${escapeHtml(timeDisplay)}</td>
                <td><strong>${escapeHtml(evt.screen || "DefaultScreen")}</strong></td>
                <td><span class="target-hint-tag font-mono text-sm">${escapeHtml(targetHintStr)}</span></td>
                <td><div style="display:flex; align-items:center;">${tierPill}<span class="action-tag ${escapeHtml(actionStr.toLowerCase())}">${escapeHtml(actionStr)}</span></div></td>
                <td><span class="conf-pill ${confLevel}">${confPct}%</span></td>
                <td><span class="status-pill ${statusClass}">${escapeHtml(statusLabel)}</span></td>
                <td class="font-mono text-sm text-cyan" title="${escapeHtml(rsPath)}">${escapeHtml(rsShortName)}</td>
                <td>
                    <button type="button" class="btn btn-secondary btn-sm btn-inspect-forensics" data-id="${safeId}" style="padding: 4px 10px; font-size: 0.76rem;">
                        Inspect
                    </button>
                </td>
            </tr>
        `;
    }).join("");

    renderPaginationBar(currentPage, totalPages, totalItems);
    attachRowClickListeners();
}

function renderPaginationBar(current, total, totalItems) {
    const infoText = document.getElementById("pagination-info");
    const btnPrev = document.getElementById("btn-page-prev");
    const btnNext = document.getElementById("btn-page-next");

    if (infoText) {
        infoText.textContent = totalItems > 0 
            ? `Menampilkan halaman ${current} dari ${total} (${totalItems} total event)`
            : "0 event ditemukan";
    }

    if (btnPrev) btnPrev.disabled = current <= 1;
    if (btnNext) btnNext.disabled = current >= total;
}

function attachRowClickListeners() {
    document.querySelectorAll(".btn-inspect-forensics").forEach(btn => {
        btn.addEventListener("click", (e) => {
            e.stopPropagation();
            if (typeof window.viewEventForensics === "function") {
                window.viewEventForensics(btn.dataset.id);
            }
        });
    });

    const tbody = document.getElementById("audit-table-body");
    if (!tbody) return;
    tbody.querySelectorAll(".audit-row").forEach(row => {
        row.addEventListener("click", (e) => {
            if (e.target.closest("button")) return;
            const id = row.dataset.id;
            const evt = appState.events.find(x => x.id === id);
            if (evt) {
                appState.setSelectedEvent(evt);
                const navDiff = document.getElementById("nav-visual-diff");
                if (navDiff) navDiff.click();
            }
        });
    });
}

function updateKpiMetrics() {
    const total = appState.events.length;
    const success = appState.events.filter(e => {
        const s = String(e.status || "").toUpperCase();
        return s === "SUCCESS" || s === "HEALED" || s === "SCROLLED";
    }).length;
    const lockouts = appState.events.filter(e => String(e.status || "").toUpperCase() === "LOCKOUT").length;
    const probes = appState.events.filter(e =>
        e.intentionTier === "CONDITIONAL_PROBE"
        || e.action === "VERIFY_PRESENT"
        || (e.method && e.method.includes("INTENT"))
        || (e.strategy && e.strategy.includes("INTENT"))
    ).length;
    const uniqueRs = new Set(
        appState.events
            .map(e => (e.generatedRs || e.rsFile || "").trim())
            .filter(p => p && p !== "-" && p !== "N/A")
    ).size;
    const latencies = appState.events
        .filter(e => e.latencyMs !== null && e.latencyMs !== undefined)
        .map(e => Number(e.latencyMs))
        .filter(n => Number.isFinite(n) && n >= 0);
    const avgLatency = latencies.length > 0
        ? Math.round(latencies.reduce((acc, n) => acc + n, 0) / latencies.length)
        : null;

    const elTotal = document.getElementById("kpi-total-healed");
    const elSuccess = document.getElementById("kpi-success-rate");
    const elLatency = document.getElementById("kpi-latency");
    const elLockouts = document.getElementById("kpi-lockouts");
    const elProbes = document.getElementById("kpi-negative-healed");
    const elSanitized = document.getElementById("kpi-sanitized-or");

    if (elTotal) elTotal.textContent = total.toLocaleString();
    if (elSuccess) elSuccess.textContent = total > 0 ? `${((success / total) * 100).toFixed(1)}%` : "0.0%";
    if (elLatency) elLatency.textContent = avgLatency !== null ? `${avgLatency} ms` : "—";
    if (elLockouts) elLockouts.textContent = lockouts.toString();
    if (elProbes) elProbes.textContent = probes.toLocaleString();
    if (elSanitized) elSanitized.textContent = uniqueRs.toLocaleString();
}
