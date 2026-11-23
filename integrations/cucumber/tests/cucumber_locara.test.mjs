import test from "node:test";
import assert from "node:assert/strict";
import { registerHooks } from "node:module";

const registeredSteps = [];

globalThis.__locaraCucumberRegister = (kind, pattern, fn) => {
    registeredSteps.push({ kind, pattern, fn });
};

registerHooks({
    resolve(specifier, context, nextResolve) {
        if (specifier === "@cucumber/cucumber") {
            return { url: "mock://cucumber", shortCircuit: true };
        }
        return nextResolve(specifier, context);
    },
    load(url, context, nextLoad) {
        if (url === "mock://cucumber") {
            return {
                format: "module",
                source: [
                    'export const When = (pattern, fn) => globalThis.__locaraCucumberRegister("When", pattern, fn);',
                    'export const Then = (pattern, fn) => globalThis.__locaraCucumberRegister("Then", pattern, fn);',
                ].join("\n"),
                shortCircuit: true,
            };
        }
        return nextLoad(url, context);
    },
});

const { toXPathLiteral } = await import("../src/index.ts");

test("toXPathLiteral safely handles plain, apostrophe, and mixed-quote values", () => {
    assert.equal(toXPathLiteral("Bayar"), "'Bayar'");
    assert.equal(toXPathLiteral("User's PIN"), `"User's PIN"`);
    const mixed = toXPathLiteral(`Owner's "VA"`);
    assert.ok(mixed.startsWith("concat("));
    assert.ok(mixed.includes("Owner"));
});

test("Cucumber TS steps reject false-green click/fill failures and missing World drivers", async () => {
    const clickStep = registeredSteps.find((s) => s.kind === "When" && s.pattern.test('Pengguna menekan tombol "Bayar"'));
    const fillStep = registeredSteps.find((s) => s.kind === "When" && s.pattern.test('Pengguna memasukkan "123" pada kolom "PIN"'));
    const verifyStep = registeredSteps.find((s) => s.kind === "Then" && s.pattern.test('Pengguna melihat status "Berhasil"'));

    assert.ok(clickStep && fillStep && verifyStep, "All universal Cucumber steps must be registered");

    await assert.rejects(
        () => clickStep.fn.call({ page: {}, locara: { click: async () => false } }, "Bayar"),
        /Failed to click/
    );
    await assert.rejects(
        () => clickStep.fn.call({}, "Bayar"),
        /No active Playwright page or Mobile driver/
    );
    await assert.rejects(
        () => fillStep.fn.call({ page: {}, locara: { fill: async () => false } }, "123", "PIN"),
        /Failed to fill/
    );
    await assert.rejects(
        () => verifyStep.fn.call({}, "Berhasil"),
        /No active Playwright page or Mobile driver/
    );
});
