/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — XML RECON LAB MODULE
 * Client-side Appium XML AST parser, heuristic matcher, and .rs generator.
 * ==============================================================================
 */

import { APP_CONFIG } from "../config.js";
import { renderIcon } from "../icons.js";
import { escapeHtml, escapeXml, toXPathLiteral } from "../utils.js";

const SAMPLE_XML = `<?xml version="1.0" encoding="UTF-8"?>
<hierarchy rotation="0">
  <android.widget.FrameLayout bounds="[0,0][1080,2400]">
    <android.widget.LinearLayout bounds="[0,0][1080,2400]">
      <!-- Header -->
      <android.widget.TextView resource-id="com.example.app:id/tv_title" text="Konfirmasi Transfer" bounds="[300,120][780,200]" />
      
      <!-- Account Info -->
      <android.widget.TextView resource-id="com.example.app:id/tv_source_acc" text="Tabungan Utama •••• 5678" bounds="[60,300][600,360]" />
      <android.widget.TextView resource-id="com.example.app:id/tv_dest_bank" text="Bank Tujuan Contoh" bounds="[60,420][700,480]" />
      <android.widget.TextView resource-id="com.example.app:id/tv_dest_acc" text="0123456789 - NASABAH CONTOH" bounds="[60,480][800,540]" />
      
      <!-- Nominal Transfer -->
      <android.widget.TextView resource-id="com.example.app:id/tv_amount" text="Rp 1.500.000" bounds="[60,600][600,680]" />
      
      <!-- Security Input -->
      <android.widget.EditText resource-id="com.example.app:id/input_security_code" content-desc="Input Security PIN 6 Digit" text="" bounds="[60,1200][1020,1350]" clickable="true" />
      
      <!-- Dangerous Element -->
      <android.widget.Button resource-id="com.example.app:id/btn_cancel" text="Batal" bounds="[60,1920][480,2060]" clickable="true" />
      
      <!-- Target Action Button -->
      <android.widget.Button resource-id="com.example.app:id/btn_next_transfer" content-desc="Tombol Lanjut Transfer Dana" text="Lanjut" bounds="[540,1920][1020,2060]" clickable="true" />
    </android.widget.LinearLayout>
  </android.widget.FrameLayout>
</hierarchy>`;

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

function runReconEngine(xmlString, targetHint) {
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(xmlString, "text/xml");

    const elements = Array.from(xmlDoc.querySelectorAll("*")).filter(el => {
        const tag = el.tagName.toLowerCase();
        return tag.includes("button") || tag.includes("edittext") || tag.includes("textview") ||
               el.getAttribute("clickable") === "true" || el.getAttribute("text") || el.getAttribute("content-desc");
    });

    const targetHintTokens = targetHint.toLowerCase().split(/\s+/).filter(Boolean);
    const dangerKeywords = APP_CONFIG.dangerKeywords;

    const candidates = elements.map(el => {
        const text = el.getAttribute("text") || "";
        const resId = el.getAttribute("resource-id") || "";
        const desc = el.getAttribute("content-desc") || "";
        const clazz = el.tagName;
        const bounds = el.getAttribute("bounds") || "[0,0][100,100]";

        const textLower = text.toLowerCase();
        const descLower = desc.toLowerCase();
        const idLower = resId.toLowerCase();

        const isDangerElement = dangerKeywords.some(dk => textLower.includes(dk) || descLower.includes(dk) || idLower.includes(dk));
        const isTargetDanger = dangerKeywords.some(dk => targetHintTokens.includes(dk));

        if (isDangerElement && !isTargetDanger) {
            return {
                element: el,
                score: 0.1,
                lockout: true,
                reason: `Polarity Mismatch: Target '${targetHint}' diblokir dari elemen destruktif '${text || resId}'`,
                text, resId, desc, clazz, bounds
            };
        }

        const textScore = computeStringScore(targetHint, text);
        const idScore = computeStringScore(targetHint, resId.split("/").pop() || "");
        const descScore = computeStringScore(targetHint, desc);
        const classScore = (clazz.toLowerCase().includes("button") || clazz.toLowerCase().includes("edittext")) ? 1.0 : 0.8;
        const proxScore = 1.0;

        let totalScore;
        if (!text) {
            totalScore = (idScore * 0.45) + (descScore * 0.35) + (classScore * 0.10) + (proxScore * 0.10);
        } else {
            totalScore = (textScore * 0.35) + (idScore * 0.25) + (descScore * 0.20) + (classScore * 0.10) + (proxScore * 0.10);
        }

        return {
            element: el,
            score: totalScore,
            lockout: false,
            vectors: { text: textScore, id: idScore, desc: descScore, clazz: classScore, proximity: proxScore },
            text, resId, desc, clazz, bounds
        };
    });

    candidates.sort((a, b) => b.score - a.score);
    const framework = detectFramework(xmlString);
    return { targetHint, winner: candidates[0] || null, framework };
}

function detectFramework(xml) {
    if (xml.includes("XCUIElementType")) {
        return { name: "iOS Native (Swift / SwiftUI)", tag: "iOS", conf: 100 };
    }
    if (xml.includes("io.flutter") || xml.includes("FlutterView")) {
        return { name: "Flutter (Dart Canvas)", tag: "Flutter", conf: 98 };
    }
    if (xml.includes("androidx.compose.ui.platform.ComposeView") || xml.includes("ComposeView")) {
        return { name: "Jetpack Compose (Modern Kotlin)", tag: "Compose", conf: 95 };
    }
    if (xml.includes("android.webkit.WebView") || xml.includes("org.chromium")) {
        return { name: "Hybrid WebView (Cordova / Capacitor)", tag: "WebView", conf: 95 };
    }
    if (xml.includes("com.facebook.react") || xml.includes("ReactViewGroup")) {
        return { name: "React Native (JavaScript / TypeScript)", tag: "RN", conf: 100 };
    }

    const vgMatches = (xml.match(/android\.view\.ViewGroup/g) || []).length;
    const allTagMatches = (xml.match(/<android\.[a-zA-Z0-9_.]+/g) || []).length;
    if (allTagMatches >= 4 && (vgMatches / allTagMatches) >= 0.35) {
        return { name: "React Native (High ViewGroup Density)", tag: "RN", conf: 92 };
    }

    return { name: "Native Android (Classic Kotlin / Java XML)", tag: "Android XML", conf: 90 };
}

function computeStringScore(s1, s2) {
    if (!s1 || !s2) return 0.0;
    const a = s1.toLowerCase().trim();
    const b = s2.toLowerCase().trim();
    if (a === b) return 1.0;
    if (b.includes(a) || a.includes(b)) return 0.9;

    const tokensA = a.split(/[\s_\-\/]+/);
    const tokensB = b.split(/[\s_\-\/]+/);
    let matches = 0;
    tokensA.forEach(ta => {
        if (tokensB.some(tb => tb.includes(ta) || ta.includes(tb))) matches++;
    });
    return Math.min(1.0, (matches / Math.max(tokensA.length, tokensB.length)) * 0.95);
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
    const safeTag = /^[A-Za-z_][A-Za-z0-9_.:-]*$/.test(w.clazz || "") ? w.clazz : "*";

    let xpathAttributes = `//${safeTag}`;
    if (w.resId && w.text) xpathAttributes += `[@resource-id=${toXPathLiteral(w.resId)} and @text=${toXPathLiteral(w.text)}]`;
    else if (w.resId) xpathAttributes += `[@resource-id=${toXPathLiteral(w.resId)}]`;
    else if (w.desc) xpathAttributes += `[@content-desc=${toXPathLiteral(w.desc)}]`;
    else if (w.text) xpathAttributes += `[@text=${toXPathLiteral(w.text)}]`;

    const xpathRelative = w.resId ? `//*[@resource-id=${toXPathLiteral(w.resId)}]//${safeTag}` : `//android.widget.LinearLayout/${safeTag}`;
    const xpathCustom = w.text ? `//${safeTag}[text()=${toXPathLiteral(w.text)} or .=${toXPathLiteral(w.text)}]` : "";
    const xpathPosition = `//${safeTag}[1]`;

    const rawRsBase = (w.resId ? w.resId.split("/").pop() : "element_healed").replace(/[^A-Za-z0-9_.-]/g, "_");
    const rsName = `${rawRsBase || "element_healed"}.rs`;
    const rsXml = `<?xml version="1.0" encoding="UTF-8"?>
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
      <value>${escapeXml(xpathAttributes)}</value>
      <webElementGuid>${generateGuid()}</webElementGuid>
   </webElementProperties>
   <webElementProperties>
      <isSelected>false</isSelected>
      <matchCondition>equals</matchCondition>
      <name>xpath:idRelative</name>
      <type>Main</type>
      <value>${escapeXml(xpathRelative)}</value>
      <webElementGuid>${generateGuid()}</webElementGuid>
   </webElementProperties>
   <webElementProperties>
      <isSelected>false</isSelected>
      <matchCondition>equals</matchCondition>
      <name>xpath:position</name>
      <type>Main</type>
      <value>${escapeXml(xpathPosition)}</value>
      <webElementGuid>${generateGuid()}</webElementGuid>
   </webElementProperties>
   <selectorCollection>
      <entry>
         <key>XPATH</key>
         <value>${escapeXml(xpathAttributes)}</value>
      </entry>
   </selectorCollection>
</WebElementEntity>`;

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
                    <span class="conf-pill ${isSuccess ? 'high' : 'low'}">
                        ${isSuccess ? 'MATCHED: ' + confPct + '%' : 'REJECTED: ' + confPct + '%'}
                    </span>
                </div>
            </div>

            <div class="recon-props-grid">
                <div class="prop-item"><span class="p-k">Resource-ID:</span> <code class="p-v">${escapeHtml(w.resId || '(empty)')}</code></div>
                <div class="prop-item"><span class="p-k">Text:</span> <code class="p-v">${escapeHtml(w.text || '(empty)')}</code></div>
                <div class="prop-item"><span class="p-k">Content-Desc:</span> <code class="p-v">${escapeHtml(w.desc || '(empty)')}</code></div>
                <div class="prop-item"><span class="p-k">Bounds:</span> <code class="p-v">${escapeHtml(w.bounds || '(empty)')}</code></div>
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
                        <code class="font-mono text-xs" style="color:var(--cyan); word-break:break-all; flex:1; margin:0 8px;">${escapeHtml(xpathAttributes)}</code>
                        <span class="badge" style="font-size:0.68rem; background:rgba(0,229,255,0.15); color:var(--cyan);">Primary</span>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center; background:rgba(255,255,255,0.03); padding:6px 10px; border-radius:4px;">
                        <span class="font-mono text-xs" style="color:var(--text-muted); min-width:120px;">xpath:idRelative</span>
                        <code class="font-mono text-xs" style="color:var(--emerald); word-break:break-all; flex:1; margin:0 8px;">${escapeHtml(xpathRelative)}</code>
                        <span class="badge" style="font-size:0.68rem; background:rgba(16,185,129,0.15); color:var(--emerald);">Relative</span>
                    </div>
                    ${xpathCustom ? `
                    <div style="display:flex; justify-content:space-between; align-items:center; background:rgba(255,255,255,0.03); padding:6px 10px; border-radius:4px;">
                        <span class="font-mono text-xs" style="color:var(--text-muted); min-width:120px;">xpath:custom</span>
                        <code class="font-mono text-xs" style="color:var(--violet); word-break:break-all; flex:1; margin:0 8px;">${escapeHtml(xpathCustom)}</code>
                        <span class="badge" style="font-size:0.68rem; background:rgba(139,92,246,0.15); color:var(--violet);">Text Anchor</span>
                    </div>` : ''}
                    <div style="display:flex; justify-content:space-between; align-items:center; background:rgba(255,255,255,0.03); padding:6px 10px; border-radius:4px;">
                        <span class="font-mono text-xs" style="color:var(--text-muted); min-width:120px;">xpath:position</span>
                        <code class="font-mono text-xs" style="color:var(--text-secondary); word-break:break-all; flex:1; margin:0 8px;">${escapeHtml(xpathPosition)}</code>
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
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
        const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}
