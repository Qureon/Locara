import { test as baseTest, expect, Locator, Page } from '@playwright/test';
import { HeuristicDomMatcher } from '../engine/heuristic-dom-matcher.js';

export interface LocaraOptions {
  probeTimeoutMs?: number;
  threshold?: number;
}

export class LocaraPageWrapper {
  private page: Page;
  private matcher: HeuristicDomMatcher;
  private defaultProbeTimeout: number;

  constructor(page: Page, options: LocaraOptions = {}) {
    this.page = page;
    this.matcher = new HeuristicDomMatcher(options.threshold ?? 0.70);
    this.defaultProbeTimeout = options.probeTimeoutMs ?? 2500;
  }

  /**
   * Clicks an element with automatic locator recovery.
   * If standard selector fails, harvests DOM candidates and clicks the healed element.
   */
  public async click(
    selector: string,
    targetHint?: string,
    options: { timeout?: number } = {}
  ): Promise<boolean> {
    const hint = targetHint || selector;
    const probeTimeout = options.timeout ?? this.defaultProbeTimeout;

    // 1. Attempt standard Playwright click first with fail-fast probe timeout
    try {
      await this.page.locator(selector).click({ timeout: probeTimeout });
      return true;
    } catch (err: any) {
      console.warn(`[Locara-Playwright] Standard click failed for '${selector}'. Initiating autonomous recovery...`);
    }

    // 2. Perform Autonomous DOM Healing
    const healedLocator = await this.healLocator(selector, hint, 'CLICK');
    if (!healedLocator) {
      throw new Error(`[Locara-Playwright] Recovery failed: No element matched '${hint}' above threshold.`);
    }

    console.info(`[Locara-Playwright] Healed '${selector}' -> Click dispatched via healed locator.`);
    await healedLocator.click({ timeout: Math.max(5000, probeTimeout) });
    return true;
  }

  /**
   * Fills an input field with automatic locator recovery.
   */
  public async fill(
    selector: string,
    text: string,
    targetHint?: string,
    options: { timeout?: number } = {}
  ): Promise<boolean> {
    const hint = targetHint || selector;
    const probeTimeout = options.timeout ?? this.defaultProbeTimeout;

    try {
      await this.page.locator(selector).fill(text, { timeout: probeTimeout });
      return true;
    } catch (err: any) {
      console.warn(`[Locara-Playwright] Standard fill failed for '${selector}'. Initiating autonomous recovery...`);
    }

    const healedLocator = await this.healLocator(selector, hint, 'FILL');
    if (!healedLocator) {
      throw new Error(`[Locara-Playwright] Recovery failed: No input field matched '${hint}' above threshold.`);
    }

    console.info(`[Locara-Playwright] Healed '${selector}' -> Fill dispatched via healed locator.`);
    await healedLocator.fill(text, { timeout: Math.max(5000, probeTimeout) });
    return true;
  }

  /**
   * Returns a self-healing Locator.
   */
  public async locator(selector: string, targetHint?: string): Promise<Locator> {
    const hint = targetHint || selector;
    try {
      const count = await this.page.locator(selector).count();
      if (count > 0) {
        return this.page.locator(selector);
      }
    } catch {}

    const healed = await this.healLocator(selector, hint, 'CLICK');
    return healed || this.page.locator(selector);
  }

  /**
   * Scans active DOM nodes and computes the winning candidate using Locara multi-vector heuristics.
   */
  private async healLocator(
    originalSelector: string,
    targetHint: string,
    action: 'CLICK' | 'FILL'
  ): Promise<Locator | null> {
    // Harvest visible interactive DOM elements from page context
    const rawElements = await this.page.evaluate(() => {
      const candidates: any[] = [];
      const nodes = Array.from(document.querySelectorAll('button, a, input, select, textarea, [role="button"], [role="link"], [data-testid]'));

      for (let i = 0; i < nodes.length; i++) {
        const el = nodes[i] as HTMLElement;
        const style = window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden' || el.hasAttribute('hidden')) continue;

        candidates.push({
          tag: el.tagName.toLowerCase(),
          id: el.id || '',
          text: (el.innerText || (el as HTMLInputElement).value || '').trim(),
          ariaLabel: el.getAttribute('aria-label') || '',
          role: el.getAttribute('role') || '',
          testId: el.getAttribute('data-testid') || el.getAttribute('data-test') || '',
          name: el.getAttribute('name') || '',
          placeholder: el.getAttribute('placeholder') || ''
        });
      }
      return candidates;
    });

    const match = this.matcher.findBestMatch(rawElements, targetHint, action);
    if (!match) {
      return null;
    }

    // Resolve optimal locator for Playwright
    if (match.testId) {
      return this.page.getByTestId(match.testId);
    }
    if (match.role && (match.ariaLabel || match.text)) {
      return this.page.getByRole(match.role as any, { name: match.ariaLabel || match.text });
    }
    if (match.ariaLabel) {
      return this.page.getByLabel(match.ariaLabel);
    }
    if (match.text && match.text.length < 40) {
      return this.page.getByText(match.text);
    }
    if (match.id) {
      return this.page.locator(`[id=${JSON.stringify(match.id)}]`);
    }

    return null;
  }
}

/**
 * Custom Playwright test runner fixture extended with Locara self-healing capabilities.
 */
export const test = baseTest.extend<{ locara: LocaraPageWrapper }>({
  locara: async ({ page }, use) => {
    const locaraWrapper = new LocaraPageWrapper(page);
    await use(locaraWrapper);
  }
});

export { expect };
