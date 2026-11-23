/**
 * Playwright MCP & Claude Marketplace Plugin Adapter for Locara by Qureon.
 * Connects Locara's Heuristic Recovery Engine with @playwright/mcp accessibility snapshots.
 *
 * Resolves stale element references ([ref=e1]) and dynamic role mutations
 * during autonomous LLM agent execution in Claude Desktop, Claude Code, and Antigravity IDE.
 */

import { isPolarityViolation } from '../engine/polarity-guard.js';

export interface McpAriaNode {
  role: string;
  name: string;
  ref?: string;
  level?: number;
  rawLine: string;
}

export class PlaywrightMcpAdapter {
  private static readonly NODE_REGEX = /^\s*-\s*([a-zA-Z0-9_-]+)(?:\s+"((?:\\.|[^"\\])*)")?(.*)$/;
  private static readonly META_BLOCK_REGEX = /\[([^\]]*)\]/g;
  private static readonly MIN_RECOVERY_SCORE = 0.70;

  /**
   * Parses an ARIA snapshot produced by @playwright/mcp `browser_snapshot` tool.
   * Format example:
   *   - heading "Checkout Transaksi" [level=2] [ref=e1]
   *   - textbox "Nomor Rekening" [ref=e2]
   *   - button "Bayar Sekarang" [disabled] [ref=e3]
   */
  public parseAriaSnapshot(snapshotText: string): McpAriaNode[] {
    if (!snapshotText) return [];

    const lines = snapshotText.split(/\r?\n/);
    const nodes: McpAriaNode[] = [];

    for (const line of lines) {
      const match = line.match(PlaywrightMcpAdapter.NODE_REGEX);
      if (match) {
        const role = match[1];
        const name = match[2] ? match[2].replace(/\\(["\\])/g, '$1') : '';
        const tail = match[3] || '';

        let ref: string | undefined;
        let level: number | undefined;

        for (const block of tail.matchAll(PlaywrightMcpAdapter.META_BLOCK_REGEX)) {
          const meta = block[1] || '';
          if (!ref) {
            const refMatch = meta.match(/\bref=([a-zA-Z0-9_-]+)/);
            if (refMatch) ref = refMatch[1];
          }
          if (level === undefined) {
            const levelMatch = meta.match(/\blevel=(\d+)/);
            if (levelMatch) level = parseInt(levelMatch[1], 10);
          }
        }

        nodes.push({
          role,
          name,
          ref,
          level,
          rawLine: line.trim()
        });
      }
    }

    return nodes;
  }

  /**
   * Finds the best matching node from an MCP snapshot given a target semantic hint.
   * Useful when an AI agent's cached [ref=eX] has expired or shifted due to DOM mutations.
   *
   * @param snapshotText Raw output from @playwright/mcp snapshot tool
   * @param targetHint Target label, e.g. "Bayar Sekarang", "Login", "Close modal"
   * @param expectedRole Optional filter, e.g. "button", "textbox"
   * @returns The recovered McpAriaNode with valid active [ref=...] or null
   */
  public recoverElementRef(
    snapshotText: string,
    targetHint: string,
    expectedRole?: string
  ): McpAriaNode | null {
    const nodes = this.parseAriaSnapshot(snapshotText);
    const cleanHint = (targetHint || '').toLowerCase().trim();
    if (nodes.length === 0 || !cleanHint) return null;

    let bestNode: McpAriaNode | null = null;
    let highestScore = 0.0;

    for (const node of nodes) {
      if (!node.ref) {
        continue;
      }
      if (expectedRole && node.role.toLowerCase() !== expectedRole.toLowerCase()) {
        continue;
      }

      const nodeName = (node.name || '').toLowerCase().trim();
      if (!nodeName || isPolarityViolation(cleanHint, nodeName)) {
        continue;
      }
      let score = 0.0;

      if (nodeName === cleanHint) {
        score = 1.0;
      } else if (nodeName.includes(cleanHint) || cleanHint.includes(nodeName)) {
        const minLen = Math.min(nodeName.length, cleanHint.length);
        const maxLen = Math.max(nodeName.length, cleanHint.length);
        score = (minLen >= 3 && (minLen / maxLen) >= 0.60) ? 0.85 : (minLen / maxLen) * 0.70;
      } else {
        // Token match
        const hintTokens = new Set(cleanHint.split(/\s+/).filter(Boolean));
        const nodeTokens = new Set(nodeName.split(/\s+/).filter(Boolean));
        let matches = 0;
        for (const t of hintTokens) {
          if (nodeTokens.has(t)) matches++;
        }
        score = matches / Math.max(1, Math.max(hintTokens.size, nodeTokens.size));
      }

      // Bonus if role matches common actionable tags
      if (['button', 'link', 'textbox'].includes(node.role.toLowerCase())) {
        score += 0.05;
      }

      if (score > highestScore && score >= PlaywrightMcpAdapter.MIN_RECOVERY_SCORE) {
        highestScore = score;
        bestNode = node;
      }
    }

    return bestNode;
  }
}
