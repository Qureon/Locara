/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — XML RECON HEURISTIC ENGINE
 * Pure XML parsing, UI framework classification, and candidate similarity scoring.
 * ==============================================================================
 */

import { APP_CONFIG } from "../config.js";

export const SAMPLE_XML = `<?xml version="1.0" encoding="UTF-8"?>
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

export function runReconEngine(xmlString, targetHint) {
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

        const totalScore = !text
            ? (idScore * 0.45) + (descScore * 0.35) + (classScore * 0.10) + (proxScore * 0.10)
            : (textScore * 0.35) + (idScore * 0.25) + (descScore * 0.20) + (classScore * 0.10) + (proxScore * 0.10);

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

export function detectFramework(xml) {
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

export function computeStringScore(s1, s2) {
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
