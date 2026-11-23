import test from "node:test";
import assert from "node:assert/strict";
import { registerHooks } from "node:module";

registerHooks({
    resolve(specifier, context, nextResolve) {
        if (specifier === "@playwright/test") {
            return { url: "mock://playwright-test", shortCircuit: true };
        }
        if (specifier.startsWith(".") && specifier.endsWith(".js") && context.parentURL?.endsWith(".ts")) {
            return nextResolve(specifier.slice(0, -3) + ".ts", context);
        }
        return nextResolve(specifier, context);
    },
    load(url, context, nextLoad) {
        if (url === "mock://playwright-test") {
            return {
                format: "module",
                source: "export const test = { extend: (fixtures) => fixtures }; export const expect = () => {};",
                shortCircuit: true,
            };
        }
        return nextLoad(url, context);
    },
});

const {
    isPolarityViolation,
    PlaywrightMcpAdapter,
    HeuristicDomMatcher,
    withLocara,
} = await import("../src/index.ts");

test("polarity-guard isolates danger and financial keywords on token boundaries", () => {
    assert.equal(isPolarityViolation("Lanjut", "Batalkan"), true);
    assert.equal(isPolarityViolation("Batalkan", "Batalkan"), false);
    assert.equal(isPolarityViolation("Lihat Detail", "Bayar Sekarang"), true);
    assert.equal(isPolarityViolation("Bayar Sekarang", "Bayar Sekarang"), false);
    assert.equal(isPolarityViolation("Status Unblocked", "Status Unblocked"), false);
});

test("PlaywrightMcpAdapter parses multi-bracket metadata, skips ref-less nodes, and enforces polarity", () => {
    const adapter = new PlaywrightMcpAdapter();
    const snapshot = [
        '- heading "Checkout \\"VIP\\"" [level=2] [ref=e1]',
        '- heading "Bayar Sekarang" [level=3]',
        '- button "Batalkan" [ref=e2]',
        '- button "Bayar Sekarang" [disabled] [ref=e4]',
    ].join("\n");

    const nodes = adapter.parseAriaSnapshot(snapshot);
    assert.equal(nodes.length, 4);
    assert.equal(nodes[0].name, 'Checkout "VIP"');
    assert.equal(nodes[0].level, 2);
    assert.equal(nodes[0].ref, "e1");
    assert.equal(nodes[1].ref, undefined);
    assert.equal(nodes[3].ref, "e4");

    const recovered = adapter.recoverElementRef(snapshot, "Bayar Sekarang");
    assert.ok(recovered);
    assert.equal(recovered.ref, "e4");
    assert.equal(recovered.role, "button");

    assert.equal(adapter.recoverElementRef('- heading "Ringkasan" [level=2]', "Ringkasan"), null);
    assert.equal(adapter.recoverElementRef('- button "Hapus Akun" [ref=e9]', "Simpan"), null);
});

test("HeuristicDomMatcher scores inputType accurately for CLICK vs FILL and clamps NaN threshold", () => {
    const matcher = new HeuristicDomMatcher(Number.NaN);
    const submitInput = { tag: "input", inputType: "submit", text: "Kirim" };
    const textInput = { tag: "input", inputType: "text", placeholder: "Kirim" };

    const clickWinner = matcher.findBestMatch([textInput, submitInput], "Kirim", "CLICK");
    assert.equal(clickWinner?.text, "Kirim");

    const fillWinner = matcher.findBestMatch([submitInput, textInput], "Kirim", "FILL");
    assert.equal(fillWinner?.placeholder, "Kirim");

    const dangerBlocked = matcher.findBestMatch(
        [{ tag: "button", text: "Hapus", id: "btn-delete" }],
        "Simpan",
        "CLICK"
    );
    assert.equal(dangerBlocked, null);
});

test("LocaraPageWrapper locator() defaults to VISIBLE and resolves non-interactive badges and long text", async () => {
    const basePage = {
        locator: (s) => ({ kind: "locator", s, count: async () => 0 }),
        getByRole: (role, opts) => ({ kind: "role", role, opts }),
        getByPlaceholder: (ph) => ({ kind: "placeholder", ph }),
        getByText: (text, opts) => ({ kind: "text", text, opts }),
    };

    const statusWrapper = withLocara(
        {
            ...basePage,
            evaluate: async () => [
                { tag: "span", text: "Transaksi Berhasil", role: "status", id: "", ariaLabel: "", inputType: "", testId: "", name: "", placeholder: "" },
            ],
        },
        { probeTimeoutMs: -100, threshold: 0.70 }
    );
    const badge = await statusWrapper.locator(".missing-badge", "Transaksi Berhasil");
    assert.equal(badge.kind, "role");
    assert.equal(badge.role, "status");

    const longText = "Pembayaran tagihan listrik pascabayar telah berhasil diproses hari ini";
    const longTextWrapper = withLocara({
        ...basePage,
        evaluate: async () => [
            { tag: "p", text: longText, role: "", id: "", ariaLabel: "", inputType: "", testId: "", name: "", placeholder: "" },
        ],
    });
    const longLoc = await longTextWrapper.locator(".missing-msg", longText);
    assert.equal(longLoc.kind, "text");
    assert.equal(longLoc.opts.exact, false);
});
