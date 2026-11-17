/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — XML RECON LAB UI MODULE
 * Client-side Recon playground event wiring, XPath display, and .rs renderer.
 * ==============================================================================
 */

import { APP_CONFIG } from "../config.js";
import { renderIcon } from "../icons.js";
import { escapeHtml, escapeXml, toXPathLiteral } from "../utils.js";
import { SAMPLE_XML, runReconEngine } from "./playground-engine.js";

export function initPlaygroundModule() {
    const btnLoad = document.getElementById("btn-load-sample");
    const btnRun = document.getElementById("btn-run-recon");
    const inputHint = document.getElementById("recon-hint");
    const inputXml = document.getElementById("recon-xml");

    if (btnLoad && inputXml) {
        btnLoad.addEventListener("click", () => {
            inputXml.value = SAMPLE_XML;
        });
    }

    if (btnRun && inputXml && inputHint) {
        btnRun.addEventListener("click", () => {
            const rawXml = inputXml.value.trim();
            const hint = inputHint.value.trim();

            if (!rawXml) {
                alert("Silakan masukkan potongan Appium XML atau klik 'Muat Sampel M-Smile'!");
                return;
            }

            const simResult = runReconEngine(rawXml, hint);
            renderReconResult(simResult);
        });
    }
}

function buildXPathSet(w) {
    const safeTag = /^[A-Za-z_][A-Za-z0-9_.:-]*$/.test(w.clazz || "") ? w.clazz : "*";
    let xpathAttributes = `//${safeTag}`;
    if (w.resId && w.text) xpathAttributes += `[@resource-id=${toXPathLiteral(w.resId)} and @text=${toXPathLiteral(w.text)}]`;
    else if (w.resId) xpathAttributes += `[@resource-id=${toXPathLiteral(w.resId)}]`;
    else if (w.desc) xpathAttributes += `[@content-desc=${toXPathLiteral(w.desc)}]`;
    else if (w.text) xpathAttributes += `[@text=${toXPathLiteral(w.text)}]`;

    const xpathRelative = w.resId
        ? `//*[@resource-id=${toXPathLiteral(w.resId)}]//${safeTag}`
        : `//android.widget.LinearLayout/${safeTag}`;
    const xpathCustom = w.text ? `//${safeTag}[text()=${toXPathLiteral(w.text)} or .=${toXPathLiteral(w.text)}]` : "";
    const xpathPosition = `//${safeTag}[1]`;
    return { xpathAttributes, xpathRelative, xpathCustom, xpathPosition };
}

function buildKatalonRsXml(w, confPct, xpaths) {
    const rawRsBase = (w.resId ? w.resId.split("/").pop() : "element_healed").replace(/[^A-Za-z0-9_.-]/g, "_");
    const rsName = `${rawRsBase || "element_healed"}.rs`;
    return `<?xml version="1.0" encoding="UTF-8"?>
<WebElementEntity>
   <description>Autonomous In-Flight Healed by Locara Engine · Qureon (Confidence: ${escapeXml(confPct)}%)</description>
   <name>${escapeXml(rsName.replace(/\.rs$/, ""))}</name>
   <tag></tag>
   <elementGuidId>${generateGuid()}</elementGuidId>
   <selectorMethod>XPATH</selectorMethod>
   <useRalativeImagePath>false</useRalativeImagePath>
   <webElementProperties>
      <isSelected>true</isSelected>
      <matchCondition>equals</matchCondition>
      <name>xpath:attributes</name>
      <type>Main</type>
      <value>${escapeXml(xpaths.xpathAttributes)}</value>
      <webElementGuid>${generateGuid()}</webElementGuid>
   </webElementProperties>
   <webElementProperties>
      <isSelected>false</isSelected>
      <matchCondition>equals</matchCondition>
      <name>xpath:idRelative</name>
      <type>Main</type>
      <value>${escapeXml(xpaths.xpathRelative)}</value>
      <webElementGuid>${generateGuid()}</webElementGuid>
   </webElementProperties>
   <webElementProperties>
      <isSelected>false</isSelected>
      <matchCondition>equals</matchCondition>
      <name>xpath:position</name>
      <type>Main</type>
      <value>${escapeXml(xpaths.xpathPosition)}</value>
      <webElementGuid>${generateGuid()}</webElementGuid>
   </webElementProperties>
   <selectorCollection>
      <entry>
         <key>XPATH</key>
         <value>${escapeXml(xpaths.xpathAttributes)}</value>
      </entry>
   </selectorCollection>
</WebElementEntity>`;
}

function renderReconResult(result) {
    const emptyBox = document.getElementById("recon-result-empty");
    const contentBox = document.getElementById("recon-result-content");
    if (!contentBox) return;

    if (emptyBox) emptyBox.classList.add("hidden");
    contentBox.classList.remove("hidden");

    const w = result.winner;
    const fw = result.framework || { name: "Detecting...", conf: 90 };

    if (!w) {
        contentBox.innerHTML = `<p class="text-danger">Tidak ada elemen yang dapat dicocokkan dari potongan XML ini.</p>`;
        return;
    }

    const confPct = (w.score * 100).toFixed(1);
    const isSuccess = w.score >= APP_CONFIG.defaultThresholds.minSimilarity && !w.lockout;
    const xpaths = buildXPathSet(w);
    const rsXml = buildKatalonRsXml(w, confPct, xpaths);

    contentBox.innerHTML = `
        <div class="recon-summary-card">
            <div class="recon-match-header">
                <div>
                    <div style="display:flex; align-items:center; gap:8px;">
                        <span class="text-xs text-muted font-mono">TARGET MATCHED:</span>
                        <span class="font-mono text-xs" style="padding: 2px 6px; border-radius: 4px; background: var(--cyan-subtle); color: var(--cyan); border: 1px solid var(--cyan-border);">Framework: ${escapeHtml(fw.name)}</span>
                        <span class="badge" style="background:rgba(16,185,129,0.15); color:var(--emerald); border:1px solid rgba(16,185,129,0.3); font-size:0.72rem; padding:2px 6px;">KS 11.7.0+ Object XPaths Table Ready</span>
                    </div>
                    <h4 class="font-mono text-cyan" style="font-size: 1.1rem; margin-top: 4px;">&lt;${escapeHtml(w.clazz)}&gt;</h4>
                </div>
                <div>
                    <span class="conf-pill ${isSuccess ? "high" : "low"}">
                        ${isSuccess ? "MATCHED: " + confPct + "%" : "REJECTED: " + confPct + "%"}
                    </span>
                </div>
            </div>

            <div class="recon-props-grid">
                <div class="prop-item"><span class="p-k">Resource-ID:</span> <code class="p-v">${escapeHtml(w.resId || "(empty)")}</code></div>
                <div class="prop-item"><span class="p-k">Text:</span> <code class="p-v">${escapeHtml(w.text || "(empty)")}</code></div>
                <div class="prop-item"><span class="p-k">Content-Desc:</span> <code class="p-v">${escapeHtml(w.desc || "(empty)")}</code></div>
                <div class="prop-item"><span class="p-k">Bounds:</span> <code class="p-v">${escapeHtml(w.bounds || "(empty)")}</code></div>
            </div>

            <!-- Katalon 11.6.0+ Object's XPaths Table Simulator -->
            <div style="margin-top: 16px; background: var(--bg-card); border: 1px solid var(--card-border); border-radius: 8px; padding: 14px;">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                    <span style="font-size:0.8rem; font-weight:700; text-transform:uppercase; letter-spacing:0.5px; color:var(--cyan);">Katalon 11.6.0+ Object's XPaths Table</span>
                    <span style="font-size:0.72rem; color:var(--text-muted);">Editable Locator Tiers</span>
                </div>
                <div style="display:flex; flex-direction:column; gap:6px;">
                    <div style="display:flex; justify-content:space-between; align-items:center; background:rgba(255,255,255,0.03); padding:6px 10px; border-radius:4px;">
                        <span class="font-mono text-xs" style="color:var(--text-muted); min-width:120px;">xpath:attributes</span>
                        <code class="font-mono text-xs" style="color:var(--cyan); word-break:break-all; flex:1; margin:0 8px;">${escapeHtml(xpaths.xpathAttributes)}</code>
                        <span class="badge" style="font-size:0.68rem; background:rgba(0,229,255,0.15); color:var(--cyan);">Primary</span>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center; background:rgba(255,255,255,0.03); padding:6px 10px; border-radius:4px;">
                        <span class="font-mono text-xs" style="color:var(--text-muted); min-width:120px;">xpath:idRelative</span>
                        <code class="font-mono text-xs" style="color:var(--emerald); word-break:break-all; flex:1; margin:0 8px;">${escapeHtml(xpaths.xpathRelative)}</code>
                        <span class="badge" style="font-size:0.68rem; background:rgba(16,185,129,0.15); color:var(--emerald);">Relative</span>
                    </div>
                    ${xpaths.xpathCustom ? `
                    <div style="display:flex; justify-content:space-between; align-items:center; background:rgba(255,255,255,0.03); padding:6px 10px; border-radius:4px;">
                        <span class="font-mono text-xs" style="color:var(--text-muted); min-width:120px;">xpath:custom</span>
                        <code class="font-mono text-xs" style="color:var(--violet); word-break:break-all; flex:1; margin:0 8px;">${escapeHtml(xpaths.xpathCustom)}</code>
                        <span class="badge" style="font-size:0.68rem; background:rgba(139,92,246,0.15); color:var(--violet);">Text Anchor</span>
                    </div>` : ""}
                    <div style="display:flex; justify-content:space-between; align-items:center; background:rgba(255,255,255,0.03); padding:6px 10px; border-radius:4px;">
                        <span class="font-mono text-xs" style="color:var(--text-muted); min-width:120px;">xpath:position</span>
                        <code class="font-mono text-xs" style="color:var(--text-secondary); word-break:break-all; flex:1; margin:0 8px;">${escapeHtml(xpaths.xpathPosition)}</code>
                        <span class="badge" style="font-size:0.68rem; background:rgba(255,255,255,0.1); color:var(--text-muted);">Hierarchy</span>
                    </div>
                </div>
            </div>

            <div class="rs-xml-box" style="margin-top:16px;">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <span class="box-title">Generated Katalon .rs Object XML</span>
                    <button class="btn btn-secondary btn-sm" id="btn-copy-playground-rs" style="padding: 4px 8px; font-size: 0.76rem;">
                        <span class="btn-icon-wrap">${renderIcon("copy")}</span> Salin XML .rs
                    </button>
                </div>
                <pre class="rs-code font-mono"><code>${escapeHtml(rsXml)}</code></pre>
            </div>
        </div>
    `;

    const copyBtn = document.getElementById("btn-copy-playground-rs");
    if (copyBtn) {
        copyBtn.addEventListener("click", () => {
            navigator.clipboard.writeText(rsXml).then(() => {
                copyBtn.innerHTML = `<span class="btn-icon-wrap">${renderIcon("check")}</span> Tersalin!`;
                setTimeout(() => {
                    copyBtn.innerHTML = `<span class="btn-icon-wrap">${renderIcon("copy")}</span> Salin XML .rs`;
                }, 2000);
            });
        });
    }
}

function generateGuid() {
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, c => {
        const r = Math.random() * 16 | 0, v = c === "x" ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}
