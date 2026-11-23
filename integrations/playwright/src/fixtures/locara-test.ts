import { test as baseTest, expect, type Locator, type Page } from '@playwright/test';
import { HeuristicDomMatcher, type RawDomNode } from '../engine/heuristic-dom-matcher.js';

const DEFAULT_MIN_ACTION_TIMEOUT_MS = 5000;
const DEFAULT_PROBE_TIMEOUT_MS = 2500;
const DEFAULT_CONFIDENCE_THRESHOLD = 0.70;
const INTERACTIVE_SELECTOR =
  'button, a, input, select, textarea, [role="button"], [role="link"], [role="textbox"], [role="checkbox"], [role="radio"], [role="tab"], [data-testid], [data-test]';
const CONTENT_SELECTOR =
  '[role="status"], [role="alert"], [aria-label], h1, h2, h3, h4, h5, h6, label, p, span, td, th, li';

export type LocaraAction = 'CLICK' | 'FILL' | 'VISIBLE' | 'GET_TEXT';

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
    this.matcher = new HeuristicDomMatcher(options.threshold ?? DEFAULT_CONFIDENCE_THRESHOLD);
    const probeMs = options.probeTimeoutMs;
    this.defaultProbeTimeout =
      typeof probeMs === 'number' && Number.isFinite(probeMs) && probeMs > 0
        ? probeMs
        : DEFAULT_PROBE_TIMEOUT_MS;
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
    } catch (_err: unknown) {
      console.warn(`[Locara-Playwright] Standard click failed for '${selector}'. Initiating autonomous recovery...`);
    }

    // 2. Perform Autonomous DOM Healing
    const healedLocator = await this.healLocator(hint, 'CLICK');
    if (!healedLocator) {
      throw new Error(`[Locara-Playwright] Recovery failed: No element matched '${hint}' above threshold.`);
    }

    console.info(`[Locara-Playwright] Healed '${selector}' -> Click dispatched via healed locator.`);
    await healedLocator.click({ timeout: Math.max(DEFAULT_MIN_ACTION_TIMEOUT_MS, probeTimeout) });
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
    } catch (_err: unknown) {
      console.warn(`[Locara-Playwright] Standard fill failed for '${selector}'. Initiating autonomous recovery...`);
    }

    const healedLocator = await this.healLocator(hint, 'FILL');
    if (!healedLocator) {
      throw new Error(`[Locara-Playwright] Recovery failed: No input field matched '${hint}' above threshold.`);
    }

    console.info(`[Locara-Playwright] Healed '${selector}' -> Fill dispatched via healed locator.`);
    await healedLocator.fill(text, { timeout: Math.max(DEFAULT_MIN_ACTION_TIMEOUT_MS, probeTimeout) });
    return true;
  }

  /**
   * Returns a self-healing Locator.
   * Defaults to 'VISIBLE' so both interactive controls and non-interactive status/text nodes are eligible.
   */
  public async locator(
    selector: string,
    targetHint?: string,
    action: LocaraAction = 'VISIBLE'
  ): Promise<Locator> {
    const hint = targetHint || selector;
    try {
      const count = await this.page.locator(selector).count();
      if (count > 0) {
        return this.page.locator(selector);
      }
    } catch {}

    const healed = await this.healLocator(hint, action);
    return healed || this.page.locator(selector);
  }

  /**
   * Scans active DOM nodes and computes the winning candidate using Locara multi-vector heuristics.
   */
  private async healLocator(
    targetHint: string,
    action: LocaraAction
  ): Promise<Locator | null> {
    const query = (action === 'VISIBLE' || action === 'GET_TEXT')
      ? `${INTERACTIVE_SELECTOR}, ${CONTENT_SELECTOR}`
      : INTERACTIVE_SELECTOR;

    const rawElements = await this.page.evaluate((selectorQuery: string): RawDomNode[] => {
      const candidates: RawDomNode[] = [];
      const seen = new Set<Element>();
      const containerTags = new Set(['p', 'span', 'li', 'td', 'th']);
      const nodes = Array.from(document.querySelectorAll(selectorQuery));

      for (let i = 0; i < nodes.length; i++) {
        const el = nodes[i] as HTMLElement;
        if (seen.has(el)) continue;
        seen.add(el);

        const style = window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden' || el.hasAttribute('hidden')) continue;

        const tag = el.tagName.toLowerCase();
        const text = (el.innerText || (el as HTMLInputElement).value || '').trim();
        const ariaLabel = el.getAttribute('aria-label') || '';
        const testId = el.getAttribute('data-testid') || el.getAttribute('data-test') || '';
        const id = el.id || '';
        if (containerTags.has(tag) && el.children.length > 0 && !ariaLabel && !testId && !id) continue;
        if (!text && !ariaLabel && !testId && !id && !el.getAttribute('name') && !el.getAttribute('placeholder')) {
          continue;
        }

        candidates.push({
          tag,
          id,
          text,
          ariaLabel,
          role: el.getAttribute('role') || '',
          inputType: tag === 'input' ? ((el as HTMLInputElement).type || '').toLowerCase() : '',
          testId,
          name: el.getAttribute('name') || '',
          placeholder: el.getAttribute('placeholder') || ''
        });
      }
      return candidates;
    }, query);

    const match = this.matcher.findBestMatch(rawElements, targetHint, action);
    if (!match) {
      return null;
    }

    if (match.testId) {
      return this.page.getByTestId(match.testId);
    }
    if (match.role && (match.ariaLabel || match.text)) {
      type PlaywrightAriaRole = Parameters<Page['getByRole']>[0];
      return this.page.getByRole(match.role as PlaywrightAriaRole, { name: match.ariaLabel || match.text });
    }
    if (match.ariaLabel) {
      return this.page.getByLabel(match.ariaLabel);
    }
    if (match.placeholder) {
      return this.page.getByPlaceholder(match.placeholder);
    }
    if (match.id) {
      return this.page.locator(`[id=${JSON.stringify(match.id)}]`);
    }
    if (match.name) {
      return this.page.locator(`${match.tag}[name=${JSON.stringify(match.name)}]`);
    }
    if (match.text) {
      return this.page.getByText(match.text, { exact: match.text.length < 40 });
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
