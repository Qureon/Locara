# 🎭 Locara for Playwright by Qureon

> **Autonomous Self-Healing, Semantic Recovery & WAI-ARIA Intelligence for Playwright**  
> Compatible with `@playwright/test`, Playwright CLI, `@playwright/mcp`, Claude Desktop/Code, and Azure Playwright Testing.

---

## ⚡ Executive Summary

Modern web applications change rapidly: CSS class names mutate (Tailwind / CSS Modules / styled-components), dynamic IDs regenerate, and layout structures shift. Traditional automation locators fail constantly, creating brittle test suites.

**Locara for Playwright** resolves this by introducing an autonomous multi-vector semantic recovery layer directly on top of Microsoft Playwright:
1. **Zero-Flake Execution**: Standard Playwright actionability and auto-waiting are preserved. If a locator fails after an aggressive probe timeout, Locara inspects the live DOM / accessibility tree, resolves the semantic intent, and heals the action without manual intervention.
2. **First-Class Java & TypeScript Parity**: Available both as native Java binding (`com.qureon.locara.playwright.PlaywrightLocara`) and modern TypeScript fixture (`@qureon/playwright-locara`).
3. **WAI-ARIA & Modern Locator Synthesis**: Prioritizes Playwright best-practice locators: `getByRole()`, `getByTestId()`, `getByLabel()`, and `getByText()`.
4. **Cloud-Scale Ready**: Fully compatible with **Microsoft Azure Playwright Testing** for parallel cloud execution across 100+ browsers.
5. **AI Agent & MCP Integration**: Integrates directly with official `@playwright/mcp` and Claude Marketplace Playwright plugin to heal stale accessibility references (`[ref=eX]`).

---

## 🚀 Quickstart (TypeScript / JavaScript)

### 1. Installation

```bash
npm install @qureon/playwright-locara --save-dev
```

### 2. Writing Self-Healing Tests

Use the extended `test` fixture from `@qureon/playwright-locara`:

```typescript
import { test, expect } from '@qureon/playwright-locara';

test('Enterprise Web Checkout with Auto-Healing', async ({ page, locara }) => {
  await page.goto('https://checkout.example.com');

  // Even if #btn-old-submit changes to a random hash, Locara recovers via 'Bayar Sekarang'
  await locara.click('#btn-old-submit', 'Bayar Sekarang');

  // Fill credentials with semantic hint recovery
  await locara.fill('input#user_field', 'demo_user_01', 'Masukkan User ID');

  // Self-healing locator
  const confirmation = await locara.locator('#status-badge', 'Konfirmasi Pembayaran');
  await expect(confirmation).toBeVisible();
});
```

### 3. Wrapping an Existing Standard Page

If you have existing helper functions or Page Object Models:

```typescript
import { withLocara } from '@qureon/playwright-locara';

export class LoginPage {
  constructor(private page: Page) {}

  async submitLogin() {
    const locara = withLocara(this.page);
    await locara.click('button.submit', 'Tombol Masuk');
  }
}
```

---

## ☕ Quickstart (Java Playwright)

For enterprise test suites running Playwright for Java (`com.microsoft.playwright.Page`):

```java
import com.microsoft.playwright.*;
import com.qureon.locara.playwright.PlaywrightLocara;

public class WebCheckoutTest {
    @Test
    void testCheckoutHealing() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            Page page = browser.newPage();
            page.navigate("https://checkout.example.com");

            // Autonomous self-healing click
            boolean clicked = PlaywrightLocara.click(page, "#btn-broken-id", "Bayar Sekarang");
            assertTrue(clicked);

            // Autonomous self-healing input fill
            boolean filled = PlaywrightLocara.fill(page, "input.user", "demo_user_01", "Masukkan User ID");
            assertTrue(filled);
        }
    }
}
```

---

## 🛠️ Playwright CLI Integration

Run tests with Playwright's rich tooling:

```bash
# Run headless suite
npx playwright test

# Interactive UI Mode with Time-Travel Debugging
npx playwright test --ui

# Record and inspect with Playwright Codegen
npx playwright codegen https://checkout.example.com

# Open deep Trace Viewer after run
npx playwright show-trace trace.zip
```

---

## ☁️ Microsoft Azure Playwright Testing Integration

Locara works out-of-the-box with Azure Playwright Testing:

```bash
# Set your Azure Playwright Testing access token
export PLAYWRIGHT_SERVICE_ACCESS_TOKEN="<your-access-token>"
export PLAYWRIGHT_SERVICE_URL="wss://<region>.playwright.microsoft.com/accounts/<id>/browsers"

# Execute across 50 parallel cloud workers
npx playwright test --config=azure-playwright.config.ts --workers=50
```

---

## 🤖 Playwright MCP & Claude Plugin Integration

Official `@playwright/mcp` outputs accessibility tree snapshots:
```yaml
- heading "Ringkasan Pembayaran" [level=2]
- textbox "Nomor Rekening" [ref=e1]
- button "Bayar Sekarang" [ref=e2]
```

When an LLM agent's cached `[ref=e2]` becomes stale due to DOM re-renders, the `PlaywrightMcpAdapter` automatically recovers the active reference:

```typescript
import { PlaywrightMcpAdapter } from '@qureon/playwright-locara';

const adapter = new PlaywrightMcpAdapter();
const healedNode = adapter.recoverElementRef(snapshotText, 'Bayar Sekarang', 'button');

console.log(healedNode?.ref); // Output: e2 (or newly reassigned active ref)
```

---

## ⚖️ Multi-Vector Scoring Profile for Modern Web

Locara applies an adaptive scoring profile tuned specifically for modern HTML5, SPAs, and WAI-ARIA:

| Scoring Vector | Web Weight | Target Semantics |
| :--- | :---: | :--- |
| **Visible Text** | `30%` | Button label, link text, placeholder, input value |
| **WAI-ARIA & Semantics** | `30%` | `aria-label`, `role`, `title`, accessibility description |
| **Test IDs & Identifiers** | `25%` | `data-testid`, `data-test`, `data-cy`, `id`, `name` |
| **HTML Tag / Role Fit** | `10%` | Role-action pairing (`<button>` for click, `<input>` for fill) |
| **Spatial / Proximity** | `5%` | Viewport visibility, element enabled state |

**Dominant Semantic Anchor**: When an exact 100% visible text match or exact `aria-label` is present, Locara prevents unmatching technical slugs from penalizing the candidate, guaranteeing instant, confident recovery.
