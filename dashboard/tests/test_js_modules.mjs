import test from "node:test";
import assert from "node:assert/strict";

import { escapeHtml, escapeXml, toXPathLiteral, escapeCodeString } from "../js/utils.js";
import { computeStringScore, detectFramework } from "../js/modules/playground-engine.js";

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
