import test from "node:test";
import assert from "node:assert/strict";

import { copyToClipboard, escapeHtml, escapeXml, toXPathLiteral, escapeCodeString } from "../js/utils.js";
import { computeStringScore, detectFramework, runReconEngine } from "../js/modules/playground-engine.js";
import { computeModuleAnalytics, computeVolatileLocators } from "../js/modules/analytics.js";
import { appState } from "../js/state.js";

test("escapeHtml escapes HTML special characters safely", () => {
    assert.equal(escapeHtml(null), "");
    assert.equal(escapeHtml(`<script>'a'&"b"</script>`), "&lt;script&gt;&#39;a&#39;&amp;&quot;b&quot;&lt;/script&gt;");
});

test("escapeXml uses &apos; for XML compatibility", () => {
    assert.equal(escapeXml(`O'Brien & Co <tag>`), "O&apos;Brien &amp; Co &lt;tag&gt;");
});

test("toXPathLiteral safely quotes apostrophes and double quotes", () => {
    assert.equal(toXPathLiteral("Lanjut"), "'Lanjut'");
    assert.equal(toXPathLiteral("User's PIN"), `"User's PIN"`);
    const mixed = toXPathLiteral(`Owner's "VA"`);
    assert.ok(mixed.startsWith("concat("));
    assert.ok(mixed.includes("Owner"));
});

test("escapeCodeString escapes slashes, quotes, dollar signs, and newlines", () => {
    assert.equal(escapeCodeString('a\\b"c$d\ne'), 'a\\\\b\\"c\\$d\\ne');
});

test("computeStringScore ranks exact, partial, token overlap, and empty inputs accurately", () => {
    assert.equal(computeStringScore("", "Lanjut"), 0.0);
    assert.equal(computeStringScore("Lanjut", "lanjut"), 1.0);
    assert.equal(computeStringScore("Transfer", "Lanjut Transfer"), 0.9);
    const partial = computeStringScore("btn_next_pay", "pay_btn_submit");
    assert.ok(partial > 0.4 && partial < 1.0);
});

test("detectFramework classifies iOS, Flutter, Compose, WebView, React Native, and Native Android", () => {
    assert.equal(detectFramework("<XCUIElementTypeButton/>").tag, "iOS");
    assert.equal(detectFramework("<io.flutter.embedding.android.FlutterView/>").tag, "Flutter");
    assert.equal(detectFramework("<androidx.compose.ui.platform.ComposeView/>").tag, "Compose");
    assert.equal(detectFramework("<android.webkit.WebView/>").tag, "WebView");
    assert.equal(detectFramework("<com.facebook.react.views.view.ReactViewGroup/>").tag, "RN");
    assert.equal(detectFramework("<hierarchy><android.widget.Button text='OK'/></hierarchy>").tag, "Android XML");
});

test("StateStore normalizes missing fields honestly without fabricating confidence, latency, or vectors", () => {
    const normalized = appState.normalizeEvent({ id: "evt-1", targetHint: "Lanjut" }, 0);
    assert.equal(normalized.status, "UNKNOWN");
    assert.equal(normalized.confidence, 0.0);
    assert.equal(normalized.latencyMs, null);
    assert.equal(normalized.vectors, null);

    appState.setEvents([{ id: "evt-1", status: "SUCCESS", confidence: 0.91, latencyMs: 22 }]);
    assert.equal(appState.selectedEvent?.id, "evt-1");

    appState.setEvents([{ id: "evt-2", status: "HEALED", confidence: 0.88 }]);
    assert.equal(appState.selectedEvent?.id, "evt-2");

    appState.setEvents([]);
    assert.equal(appState.selectedEvent, null);
});

test("runReconEngine reports parseError on malformed DOMParser XML output", () => {
    const prevDomParser = globalThis.DOMParser;
    globalThis.DOMParser = class MockDomParser {
        parseFromString() {
            return {
                querySelector(sel) {
                    return sel === "parsererror" ? { textContent: "XML syntax error" } : null;
                },
                querySelectorAll() {
                    return [];
                }
            };
        }
    };
    try {
        const res = runReconEngine("<broken><xml", "Lanjut");
        assert.ok(res.parseError && res.parseError.length > 0);
        assert.equal(res.winner, null);
    } finally {
        globalThis.DOMParser = prevDomParser;
    }
});

test("copyToClipboard rejects cleanly when navigator.clipboard is unavailable", async () => {
    await assert.rejects(() => copyToClipboard("sample"), /Clipboard API unavailable/);
});

test("StateStore sorts nullable fields deterministically and analytics aggregates live events", () => {
    appState.setEvents([
        { id: "e1", module: "Transfer", brokenLocator: "#old_pay", status: "SUCCESS", confidence: 0.9, latencyMs: null },
        { id: "e2", module: "Transfer", brokenLocator: "#old_pay", status: "HEALED", confidence: 0.85, latencyMs: 18 },
        { id: "e3", module: "Login", brokenLocator: "#old_login", status: "SUCCESS", confidence: 0.95, latencyMs: 42 },
    ]);
    appState.setSort("latencyMs");
    appState.setSort("latencyMs"); // ascending
    const sorted = appState.getFilteredAndSortedEvents();
    assert.deepEqual(sorted.map(e => e.id), ["e2", "e3", "e1"]);

    const mods = computeModuleAnalytics(appState.events);
    assert.equal(mods[0].module, "Transfer");
    assert.equal(mods[0].count, 2);

    const volatile = computeVolatileLocators(appState.events);
    assert.equal(volatile[0].id, "#old_pay");
    assert.equal(volatile[0].changes, 2);
});
