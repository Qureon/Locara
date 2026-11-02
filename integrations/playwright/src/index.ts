/**
 * Locara by Qureon: Autonomous Self-Healing & Semantic Recovery Engine for Playwright
 * Universal exports for Playwright Test, MCP, and Azure Playwright Testing.
 */

export * from './fixtures/locara-test.js';
export * from './engine/heuristic-dom-matcher.js';
export * from './mcp/playwright-mcp-adapter.js';

import { LocaraPageWrapper } from './fixtures/locara-test.js';
import { Page } from '@playwright/test';

/**
 * Utility helper to wrap an existing standard Playwright Page with Locara auto-healing.
 */
export function withLocara(page: Page, options?: { probeTimeoutMs?: number; threshold?: number }): LocaraPageWrapper {
  return new LocaraPageWrapper(page, options);
}
