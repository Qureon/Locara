/**
 * Locara by Qureon: Autonomous Self-Healing & Semantic Recovery Engine for Playwright
 * Universal exports for Playwright Test, MCP, and Azure Playwright Testing.
 */

export * from './fixtures/locara-test.js';
export * from './engine/heuristic-dom-matcher.js';
export * from './engine/polarity-guard.js';
export * from './mcp/playwright-mcp-adapter.js';

import { LocaraPageWrapper, type LocaraOptions } from './fixtures/locara-test.js';
import type { Page } from '@playwright/test';

/**
 * Utility helper to wrap an existing standard Playwright Page with Locara auto-healing.
 */
export function withLocara(page: Page, options?: LocaraOptions): LocaraPageWrapper {
  return new LocaraPageWrapper(page, options);
}
