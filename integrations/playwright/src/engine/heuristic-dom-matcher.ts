/**
 * Heuristic DOM Matcher for Playwright Web Automation.
 * Client-side TypeScript port of Locara's Multi-Vector Scoring Engine.
 * Evaluates candidates based on Text, WAI-ARIA, Test-ID, Element ID, and Role.
 */

export interface CandidateElement {
  tag: string;
  id: string;
  text: string;
  ariaLabel: string;
  role: string;
  testId: string;
  name: string;
  placeholder: string;
  xpath: string;
  cssSelector: string;
  playwrightLocatorRecommendation: string;
  confidenceScore: number;
}

export class HeuristicDomMatcher {
  private minConfidenceThreshold: number;

  constructor(threshold = 0.70) {
    this.minConfidenceThreshold = threshold;
  }

  /**
   * Evaluates and ranks candidate DOM elements against a target semantic hint and action.
   */
  public findBestMatch(
    elements: Array<{
      tag: string;
      id?: string;
      text?: string;
      ariaLabel?: string;
      role?: string;
      testId?: string;
      name?: string;
      placeholder?: string;
      xpath?: string;
      cssSelector?: string;
    }>,
    targetHint: string,
    action: 'CLICK' | 'FILL' | 'VISIBLE' | 'GET_TEXT' = 'CLICK'
  ): CandidateElement | null {
    if (!elements || elements.length === 0 || !targetHint) {
      return null;
    }

    const cleanHint = this.sanitizeHint(targetHint);
    const rankedCandidates: CandidateElement[] = [];

    for (const el of elements) {
      const tag = (el.tag || '').toLowerCase();
      const id = el.id || '';
      const text = (el.text || '').trim();
      const ariaLabel = el.ariaLabel || '';
      const role = el.role || '';
      const testId = el.testId || '';
      const name = el.name || '';
      const placeholder = el.placeholder || '';

      // Vector 1: Text Similarity
      const textScore = Math.max(
        this.calculateCompositeScore(text, cleanHint),
        this.calculateCompositeScore(placeholder, cleanHint)
      );

      // Vector 2: ARIA & Accessibility Similarity
      const ariaScore = this.calculateCompositeScore(ariaLabel, cleanHint);

      // Vector 3: Test ID Similarity
      const testIdScore = this.calculateCompositeScore(testId, cleanHint);

      // Vector 4: ID & Name Attribute Similarity
      const idScore = Math.max(
        this.calculateCompositeScore(id, cleanHint),
        this.calculateCompositeScore(name, cleanHint)
      );

      // Vector 5: Action & Role Compatibility
      const actionScore = this.calculateActionCompatibility(tag, role, action);

      // Dominant Semantic Anchor Scoring
      const maxSemantic = Math.max(textScore, ariaScore, testIdScore, idScore);
      let totalConfidence: number;

      if (maxSemantic >= 0.80) {
        // High Anchor: Dominant vector gives high signal, blend softly with secondary signals
        const secondary = (textScore + ariaScore + testIdScore + idScore - maxSemantic) / 3.0;
        const blendedSemantic = maxSemantic * 0.85 + secondary * 0.15;
        totalConfidence = blendedSemantic * 0.80 + actionScore * 0.20;
      } else {
        // Standard Multi-Vector Weighted Average for Web (Text: 0.30, ARIA: 0.25, TestID: 0.20, ID: 0.15, Action: 0.10)
        totalConfidence =
          textScore * 0.30 +
          ariaScore * 0.25 +
          testIdScore * 0.20 +
          idScore * 0.15 +
          actionScore * 0.10;
      }

      totalConfidence = Math.min(1.0, Math.max(0.0, totalConfidence));

      if (totalConfidence >= this.minConfidenceThreshold) {
        const recommendation = this.synthesizePlaywrightLocator(el, text, ariaLabel, testId, role);
        rankedCandidates.push({
          tag,
          id,
          text,
          ariaLabel,
          role,
          testId,
          name,
          placeholder,
          xpath: el.xpath || `//${tag}`,
          cssSelector: el.cssSelector || `${tag}`,
          playwrightLocatorRecommendation: recommendation,
          confidenceScore: totalConfidence
        });
      }
    }

    if (rankedCandidates.length === 0) {
      return null;
    }

    // Sort descending by confidence score
    rankedCandidates.sort((a, b) => b.confidenceScore - a.confidenceScore);
    return rankedCandidates[0];
  }

  /**
   * Synthesizes idiomatic Playwright locators conforming to modern best practices:
   * 1. getByRole (Recommended by Playwright docs)
   * 2. getByTestId
   * 3. getByLabel / aria-label
   * 4. getByText
   * 5. XPath fallback
   */
  private synthesizePlaywrightLocator(
    el: any,
    text: string,
    ariaLabel: string,
    testId: string,
    role: string
  ): string {
    if (testId) {
      return `getByTestId('${testId}')`;
    }
    if (role && (text || ariaLabel)) {
      const name = ariaLabel || text;
      return `getByRole('${role}', { name: '${name.replace(/'/g, "\\'")}' })`;
    }
    if (ariaLabel) {
      return `getByLabel('${ariaLabel.replace(/'/g, "\\'")}')`;
    }
    if (text && text.length < 50) {
      return `getByText('${text.replace(/'/g, "\\'")}')`;
    }
    if (el.id) {
      return `locator('#${el.id}')`;
    }
    return el.xpath ? `locator('xpath=${el.xpath}')` : `locator('${el.tag}')`;
  }

  private calculateActionCompatibility(tag: string, role: string, action: string): number {
    if (action === 'CLICK') {
      if (tag === 'button' || role === 'button' || tag === 'a' || role === 'link') return 1.0;
      if (tag === 'input' && ['submit', 'button', 'checkbox', 'radio'].includes(role)) return 1.0;
      return 0.70;
    }
    if (action === 'FILL') {
      if (tag === 'input' || tag === 'textarea' || role === 'textbox') return 1.0;
      return 0.50;
    }
    return 0.85;
  }

  private sanitizeHint(hint: string): string {
    return hint
      .replace(/^[0-9]+(?:-[0-9]+)?\.\s*/, '')
      .replace(/^(?:Button|Tombol|Input|Field|Label|Link)\s*-\s*/i, '')
      .trim();
  }

  private calculateCompositeScore(s1: string, s2: string): number {
    if (!s1 || !s2) return 0.0;
    const a = s1.toLowerCase().trim();
    const b = s2.toLowerCase().trim();

    if (a === b) return 1.0;
    if (a.includes(b) || b.includes(a)) {
      const minLen = Math.min(a.length, b.length);
      const maxLen = Math.max(a.length, b.length);
      return Math.max(0.85, minLen / maxLen);
    }

    // Token overlap
    const tokensA = new Set(a.split(/[\s_\-./]+/));
    const tokensB = new Set(b.split(/[\s_\-./]+/));
    let intersection = 0;
    for (const t of tokensA) {
      if (tokensB.has(t)) intersection++;
    }
    const overlap = intersection / Math.max(tokensA.size, tokensB.size);
    return overlap;
  }
}
