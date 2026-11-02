/**
 * Playwright MCP & Claude Marketplace Plugin Adapter for Locara by Qureon.
 * Connects Locara's Heuristic Recovery Engine with @playwright/mcp accessibility snapshots.
 *
 * Resolves stale element references ([ref=e1]) and dynamic role mutations
 * during autonomous LLM agent execution in Claude Desktop, Claude Code, and Antigravity IDE.
 */

export interface McpAriaNode {
  role: string;
  name: string;
  ref?: string;
  level?: number;
  rawLine: string;
}

export class PlaywrightMcpAdapter {
  private static readonly NODE_REGEX = /^\s*-\s*([a-zA-Z0-9_-]+)(?:\s+"([^"]*)")?(?:\s*\[([^\]]*)\])?/;

  /**
   * Parses an ARIA snapshot produced by @playwright/mcp `browser_snapshot` tool.
   * Format example:
   *   - heading "Checkout Transaksi" [level=2]
   *   - textbox "Nomor Rekening" [ref=e1]
   *   - button "Bayar Sekarang" [ref=e2]
   */
  public parseAriaSnapshot(snapshotText: string): McpAriaNode[] {
    if (!snapshotText) return [];

    const lines = snapshotText.split('\n');
    const nodes: McpAriaNode[] = [];

    for (const line of lines) {
      const match = line.match(PlaywrightMcpAdapter.NODE_REGEX);
      if (match) {
        const role = match[1];
        const name = match[2] || '';
        const meta = match[3] || '';

        let ref: string | undefined;
        let level: number | undefined;

        if (meta) {
          const refMatch = meta.match(/ref=([a-zA-Z0-9_-]+)/);
          if (refMatch) ref = refMatch[1];

          const levelMatch = meta.match(/level=(\d+)/);
          if (levelMatch) level = parseInt(levelMatch[1], 10);
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
    if (nodes.length === 0 || !targetHint) return null;

    const cleanHint = targetHint.toLowerCase().trim();
    let bestNode: McpAriaNode | null = null;
    let highestScore = 0.0;

    for (const node of nodes) {
      if (expectedRole && node.role.toLowerCase() !== expectedRole.toLowerCase()) {
        continue;
      }

      const nodeName = (node.name || '').toLowerCase().trim();
      let score = 0.0;

      if (nodeName === cleanHint) {
        score = 1.0;
      } else if (nodeName.includes(cleanHint) || cleanHint.includes(nodeName)) {
        score = 0.85;
      } else {
        // Token match
        const hintTokens = new Set(cleanHint.split(/\s+/));
        const nodeTokens = new Set(nodeName.split(/\s+/));
        let matches = 0;
        for (const t of hintTokens) {
          if (nodeTokens.has(t)) matches++;
        }
        score = matches / Math.max(hintTokens.size, nodeTokens.size);
      }

      // Bonus if role matches common actionable tags
      if (['button', 'link', 'textbox'].includes(node.role.toLowerCase())) {
        score += 0.05;
      }

      if (score > highestScore && score >= 0.70) {
        highestScore = score;
        bestNode = node;
      }
    }

    return bestNode;
  }
}
