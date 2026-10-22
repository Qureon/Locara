/**
 * ==============================================================================
 * ZERO-HEALING DASHBOARD — XML RECON LAB MODULE
 * Client-side Appium XML AST parser, heuristic matcher, and .rs generator.
 * ==============================================================================
 */

import { APP_CONFIG } from "../config.js";
import { renderIcon } from "../icons.js";

const SAMPLE_XML = `<?xml version="1.0" encoding="UTF-8"?>
<hierarchy rotation="0">
  <android.widget.FrameLayout bounds="[0,0][1080,2400]">
    <android.widget.LinearLayout bounds="[0,0][1080,2400]">
      <!-- Header -->
      <android.widget.TextView resource-id="com.bankmega.msmile:id/tv_title" text="Konfirmasi Transfer" bounds="[300,120][780,200]" />
      
      <!-- Account Info -->
      <android.widget.TextView resource-id="com.bankmega.msmile:id/tv_source_acc" text="Mega Dana •••• 5678" bounds="[60,300][600,360]" />
      <android.widget.TextView resource-id="com.bankmega.msmile:id/tv_dest_bank" text="Bank Central Asia (BCA)" bounds="[60,420][700,480]" />
      <android.widget.TextView resource-id="com.bankmega.msmile:id/tv_dest_acc" text="0123456789 - SITI AMINAH" bounds="[60,480][800,540]" />
      
      <!-- Nominal Transfer -->
      <android.widget.TextView resource-id="com.bankmega.msmile:id/tv_amount" text="Rp 1.500.000" bounds="[60,600][600,680]" />
      
      <!-- Security Input -->
      <android.widget.EditText resource-id="com.bankmega.msmile:id/input_security_code" content-desc="Input M-PIN 6 Digit" text="" bounds="[60,1200][1020,1350]" clickable="true" />
      
      <!-- Dangerous Element -->
      <android.widget.Button resource-id="com.bankmega.msmile:id/btn_cancel" text="Batal" bounds="[60,1920][480,2060]" clickable="true" />
      
      <!-- Target Action Button -->
      <android.widget.Button resource-id="com.bankmega.msmile:id/btn_next_transfer" content-desc="Tombol Lanjut Transfer Dana" text="Lanjut" bounds="[540,1920][1020,2060]" clickable="true" />
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

    const targetHintTokens = targetHint.toLowerCase().split(/\s+/);
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

    let optimalXPath = `//${w.clazz}`;
    if (w.resId) optimalXPath += `[@resource-id='${w.resId}']`;
    if (w.text) optimalXPath += (w.resId ? `[contains(@text, '${w.text}')]` : `[@text='${w.text}']`);

    const rsName = (w.resId ? w.resId.split("/").pop() : "element_healed") + ".rs";
    const rsXml = `<?xml version="1.0" encoding="UTF-8"?>
<WebElementEntity>
   <description>Autonomous In-Flight Healed by Locara Engine · Qureon (Confidence: ${confPct}%)</description>
   <name>${rsName.replace(".rs", "")}</name>
   <tag></tag>
   <elementGuidId>${generateGuid()}</elementGuidId>
   <selectorMethod>BASIC</selectorMethod>
   <useRalativeImagePath>false</useRalativeImagePath>
   <webElementProperties>
      <isSelected>true</isSelected>
      <matchCondition>equals</matchCondition>
      <name>xpath</name>
      <type>Main</type>
      <value>${optimalXPath}</value>
   </webElementProperties>
</WebElementEntity>`;

    contentBox.innerHTML = `
        <div class="recon-summary-card">
            <div class="recon-match-header">
                <div>
                    <div style="display:flex; align-items:center; gap:8px;">
                        <span class="text-xs text-muted font-mono">TARGET MATCHED:</span>
                        <span class="font-mono text-xs" style="padding: 2px 6px; border-radius: 4px; background: var(--cyan-subtle); color: var(--cyan); border: 1px solid var(--cyan-border);">Framework: ${escapeHtml(fw.name)}</span>
                    </div>
                    <h4 class="font-mono text-cyan" style="font-size: 1.1rem; margin-top: 4px;">&lt;${w.clazz}&gt;</h4>
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

            <div class="xpath-preview-box">
                <span class="box-title">Synthesized Dynamic XPath</span>
                <code class="font-mono" style="color: var(--cyan); word-break: break-all;">${escapeHtml(optimalXPath)}</code>
            </div>

            <div class="rs-xml-box">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <span class="box-title">Katalon Object Repository (.rs) XML</span>
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

function escapeHtml(str) {
    if (!str) return "";
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
