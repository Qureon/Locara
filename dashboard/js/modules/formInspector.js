/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — SPATIAL & FORM INSPECTOR MODULE (v1.5.0)
 * Visualizes 2D bounding box Left-to-Right and Top-to-Bottom field alignment,
 * section boundary detection, and key-value receipt pairing.
 * ==============================================================================
 */

import { extractFields } from "../api.js";
import { renderIcon } from "../icons.js";

const SAMPLE_TRANSACTION_XML = `<?xml version="1.0" encoding="UTF-8"?>
<hierarchy rotation="0">
  <android.widget.FrameLayout bounds="[0,0][1080,2400]">
    <android.widget.LinearLayout bounds="[0,0][1080,2400]">
      <!-- Header Screen -->
      <android.widget.TextView text="Detail Konfirmasi Transaksi" bounds="[60,140][900,220]" />
      
      <!-- Section: Detail Rekening -->
      <android.widget.TextView text="Rekening Sumber" bounds="[60,320][450,380]" />
      <android.widget.TextView text="Tabungan Utama •••• 9876" bounds="[460,320][1020,380]" />

      <android.widget.TextView text="Nama Pengirim" bounds="[60,400][450,460]" />
      <android.widget.TextView text="BUDI PRASETYO" bounds="[460,400][1020,460]" />

      <android.widget.TextView text="Nomor Tujuan" bounds="[60,480][450,540]" />
      <android.widget.TextView text="081234567890" bounds="[460,480][1020,540]" />

      <!-- Section: Rincian Pembayaran -->
      <android.widget.TextView text="Nominal Paket" bounds="[60,620][450,680]" />
      <android.widget.TextView text="Rp 100.000" bounds="[460,620][1020,680]" />

      <android.widget.TextView text="Biaya Admin" bounds="[60,700][450,760]" />
      <android.widget.TextView text="Rp 1.500" bounds="[460,700][1020,760]" />

      <!-- Terminal Total Row -->
      <android.widget.TextView text="Total Pembayaran" bounds="[60,820][450,900]" />
      <android.widget.TextView text="Rp 101.500" bounds="[460,820][1020,900]" />

      <!-- Bottom Form Button -->
      <android.widget.Button text="Konfirmasi Bayar" bounds="[60,2100][1020,2240]" clickable="true" />
    </android.widget.LinearLayout>
  </android.widget.FrameLayout>
</hierarchy>`;

export function initFormInspectorModule() {
    const btnLoad = document.getElementById("btn-load-form-sample");
    const btnRun = document.getElementById("btn-run-spatial-extract");
    const inputXml = document.getElementById("form-inspector-xml");
    const outputEmpty = document.getElementById("form-result-empty");
    const outputWrapper = document.getElementById("form-result-content");

    if (btnLoad && inputXml) {
        btnLoad.addEventListener("click", () => {
            inputXml.value = SAMPLE_TRANSACTION_XML;
        });
    }

    if (btnRun && inputXml) {
        btnRun.addEventListener("click", async () => {
            const xml = inputXml.value.trim();
            if (!xml) {
                alert("Harap masukkan atau muat Appium Page Source XML terlebih dahulu!");
                return;
            }

            btnRun.classList.add("loading");
            btnRun.disabled = true;

            try {
                let res = await extractFields(xml);
                
                // Client-side fallback if server offline or standalone
                if (!res || !res.pairs || res.pairs.length === 0) {
                    res = parseXmlSpatialClient(xml);
                }

                renderSpatialResults(res.pairs || [], outputEmpty, outputWrapper);
            } catch (err) {
                console.error("Spatial extraction error:", err);
                const fallback = parseXmlSpatialClient(xml);
                renderSpatialResults(fallback.pairs || [], outputEmpty, outputWrapper);
            } finally {
                btnRun.classList.remove("loading");
                btnRun.disabled = false;
            }
        });
    }
}

function renderSpatialResults(pairs, emptyEl, wrapperEl) {
    if (!pairs || pairs.length === 0) {
        if (emptyEl) emptyEl.classList.remove("hidden");
        if (wrapperEl) wrapperEl.classList.add("hidden");
        return;
    }

    if (emptyEl) emptyEl.classList.add("hidden");
    if (wrapperEl) wrapperEl.classList.remove("hidden");

    let rowsHtml = "";
    pairs.forEach((p, idx) => {
        const isTotal = /total|subtotal|jumlah bayar/i.test(p.label);
        const scorePct = Math.round((p.score || 0.95) * 100);
        const badgeClass = isTotal ? "badge-warning" : "badge-success";
        const tagLabel = isTotal ? "TERMINAL TOTAL" : "ROW FIELD";

        rowsHtml += `
            <tr class="audit-row ${isTotal ? 'highlight-row' : ''}">
                <td style="font-weight: 600; color: var(--text-primary);">
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <span>${escapeHtml(p.label)}</span>
                        <span class="badge ${badgeClass}" style="font-size: 0.7rem; padding: 2px 6px;">${tagLabel}</span>
                    </div>
                </td>
                <td style="font-family: var(--font-mono); color: var(--cyan); font-weight: 600;">
                    ${escapeHtml(p.value)}
                </td>
                <td>
                    <div style="display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 0.82rem; font-weight: 600;">${scorePct}%</span>
                        <div style="flex: 1; height: 6px; background: var(--bg-hover); border-radius: 3px; overflow: hidden; min-width: 50px;">
                            <div style="width: ${scorePct}%; height: 100%; background: ${scorePct > 85 ? 'var(--emerald)' : 'var(--amber)'};"></div>
                        </div>
                    </div>
                </td>
                <td style="font-family: var(--font-mono); font-size: 0.78rem; color: var(--text-muted);">
                    ${escapeHtml(p.bounds || '-')}
                </td>
                <td>
                    <button class="btn btn-sm btn-secondary copy-field-btn" data-key="${escapeHtml(p.label)}" data-val="${escapeHtml(p.value)}" title="Salin kode verifikasi Katalon">
                        <span class="btn-icon-wrap" data-icon="copy"></span> Copy Code
                    </button>
                </td>
            </tr>
        `;
    });

    wrapperEl.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
            <div style="display: flex; align-items: center; gap: 10px;">
                <h4 style="font-size: 0.95rem; font-weight: 600; margin: 0;">Terdeteksi ${pairs.length} Pasang Field Spasial</h4>
                <span class="badge badge-accent" style="font-size: 0.75rem;">Universal Spatial Engine v1.5.0</span>
            </div>
            <button class="btn btn-sm btn-secondary" id="btn-copy-all-json">
                <span class="btn-icon-wrap" data-icon="download"></span> Export JSON
            </button>
        </div>

        <div class="table-container" style="max-height: 480px; overflow-y: auto;">
            <table class="audit-table">
                <thead>
                    <tr>
                        <th style="width: 30%;">Label (Key)</th>
                        <th style="width: 30%;">Extracted Value</th>
                        <th style="width: 15%;">Spatial Match</th>
                        <th style="width: 15%;">2D Bounding Box</th>
                        <th style="width: 10%;">Aksi</th>
                    </tr>
                </thead>
                <tbody>
                    ${rowsHtml}
                </tbody>
            </table>
        </div>
    `;

    // Re-inject copy icons
    wrapperEl.querySelectorAll("[data-icon]").forEach(el => {
        el.innerHTML = renderIcon(el.dataset.icon);
    });

    // Wire individual copy button
    wrapperEl.querySelectorAll(".copy-field-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            const key = btn.dataset.key;
            const val = btn.dataset.val;
            const snippet = `// Locara v1.5.0 Spatial Field Assertion\nString actual = Locara.getFieldValue("${key}")\nassert actual == "${val}" : "Expected ${key} to be '${val}' but got '$actual'"`;
            navigator.clipboard.writeText(snippet).then(() => {
                const orig = btn.innerHTML;
                btn.innerHTML = `<span style="color: var(--emerald);">✓ Copied</span>`;
                setTimeout(() => btn.innerHTML = orig, 1500);
            });
        });
    });

    // Wire Export All JSON
    const btnExportJson = document.getElementById("btn-copy-all-json");
    if (btnExportJson) {
        btnExportJson.addEventListener("click", () => {
            const jsonStr = JSON.stringify(pairs, null, 2);
            navigator.clipboard.writeText(jsonStr).then(() => {
                alert("Seluruh pasangan field berhasil disalin ke clipboard dalam format JSON!");
            });
        });
    }
}

/**
 * Fallback browser-side spatial parser using DOMParser for XML
 */
function parseXmlSpatialClient(xmlText) {
    try {
        const parser = new DOMParser();
        const doc = parser.parseFromString(xmlText, "text/xml");
        const elements = Array.from(doc.querySelectorAll("*"));
        const boundsPattern = /\[(\d+),(\d+)\]\[(\d+),(\d+)\]/;

        const nodes = [];
        elements.forEach(el => {
            const text = (el.getAttribute("text") || "").trim();
            const desc = (el.getAttribute("content-desc") || "").trim();
            const bStr = el.getAttribute("bounds") || "";
            const m = bStr.match(boundsPattern);
            if (m) {
                const [_, x1, y1, x2, y2] = m.map(Number);
                nodes.push({
                    text: text,
                    desc: desc,
                    x1, y1, x2, y2,
                    w: x2 - x1,
                    h: y2 - y1,
                    clickable: el.getAttribute("clickable") === "true"
                });
            }
        });

        const pairs = [];
        const labels = nodes.filter(n => n.text && n.w > 0);
        const claimed = new Set();

        labels.forEach(lbl => {
            let bestVal = null;
            let bestScore = 0;

            nodes.forEach(cand => {
                if (cand === lbl || claimed.has(cand)) return;

                // Left-to-right alignment check
                if (cand.x1 >= lbl.x1 && cand.x2 > lbl.x2) {
                    const vOverlap = Math.max(0, Math.min(lbl.y2, cand.y2) - Math.max(lbl.y1, cand.y1));
                    const minH = Math.max(1, Math.min(lbl.h, cand.h));
                    const overlapRatio = vOverlap / minH;

                    if (overlapRatio >= 0.40 && (cand.text || cand.desc || cand.clickable)) {
                        const hDist = Math.max(0, cand.x1 - lbl.x2);
                        const score = overlapRatio * 0.6 + (1.0 / (1.0 + hDist / 100.0)) * 0.4;
                        if (score > bestScore) {
                            bestScore = score;
                            bestVal = cand;
                        }
                    }
                }
            });

            if (bestVal && (bestVal.text || bestVal.desc)) {
                const valText = bestVal.text || bestVal.desc;
                if (valText !== lbl.text) {
                    claimed.add(bestVal);
                    pairs.push({
                        label: lbl.text,
                        value: valText,
                        score: Number(bestScore.toFixed(2)),
                        bounds: `[${lbl.x1},${lbl.y1}][${bestVal.x2},${bestVal.y2}]`
                    });
                }
            }
        });

        return { pairs };
    } catch (e) {
        console.error("Client spatial parse error:", e);
        return { pairs: [] };
    }
}

function escapeHtml(str) {
    if (!str) return "";
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
