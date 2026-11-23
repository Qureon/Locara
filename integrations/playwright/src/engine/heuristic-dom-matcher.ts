/**
 * Heuristic DOM Matcher for Playwright Web Automation.
 * Client-side TypeScript port of Locara's Multi-Vector Scoring Engine.
 * Evaluates candidates based on Text, WAI-ARIA, Test-ID, Element ID, and Role.
 */

import { isPolarityViolation } from './polarity-guard.js';

export interface RawDomNode {
  tag: string;
  id?: string;
  text?: string;
  ariaLabel?: string;
  role?: string;
  inputType?: string;
  testId?: string;
  testIdAttr?: 'data-testid' | 'data-test';
  name?: string;
  placeholder?: string;
  xpath?: string;
  cssSelector?: string;
}

export interface CandidateElement {
  tag: string;
  id: string;
  text: string;
  ariaLabel: string;
  role: string;
  testId: string;
  testIdAttr?: 'data-testid' | 'data-test';
  name: string;
  placeholder: string;
  xpath: string;
  cssSelector: string;
  playwrightLocatorRecommendation: string;
  confidenceScore: number;
}

export class HeuristicDomMatcher {
  private static readonly CLICKABLE_INPUT_TYPES = new Set(['submit', 'button', 'checkbox', 'radio', 'reset']);
  private static readonly DEFAULT_THRESHOLD = 0.70;
  private minConfidenceThreshold: number;

  constructor(threshold = HeuristicDomMatcher.DEFAULT_THRESHOLD) {
    this.minConfidenceThreshold = Number.isFinite(threshold)
      ? Math.min(1.0, Math.max(0.0, threshold))
      : HeuristicDomMatcher.DEFAULT_THRESHOLD;
  }

  /**
   * Evaluates and ranks candidate DOM elements against a target semantic hint and action.
   */
  public findBestMatch(
    elements: RawDomNode[],
    targetHint: string,
    action: 'CLICK' | 'FILL' | 'VISIBLE' | 'GET_TEXT' = 'CLICK'
  ): CandidateElement | null {
    if (!elements || elements.length === 0 || !targetHint) {
      return null;
    }

    const cleanHint = this.sanitizeHint(targetHint);
    if (!cleanHint) {
      return null;
    }
    const rankedCandidates: CandidateElement[] = [];

    for (const el of elements) {
      const tag = (el.tag || '').toLowerCase();
      const id = el.id || '';
      const text = (el.text || '').trim();
      const ariaLabel = el.ariaLabel || '';
      const role = el.role || '';
      const inputType = (el.inputType || '').toLowerCase();
      const testId = el.testId || '';
      const name = el.name || '';
      const placeholder = el.placeholder || '';

      if (isPolarityViolation(cleanHint, text, ariaLabel, testId, id, name)) {
        continue;
      }

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
      const actionScore = this.calculateActionCompatibility(tag, role, action, inputType);

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
          testIdAttr: el.testIdAttr,
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
  private quoteJsString(value: string): string {
    return value
      .replace(/\\/g, '\\\\')
      .replace(/'/g, "\\'")
      .replace(/\r/g, '\\r')
      .replace(/\n/g, '\\n');
  }

  private synthesizePlaywrightLocator(
    el: RawDomNode,
    text: string,
    ariaLabel: string,
    testId: string,
    role: string
  ): string {
    if (testId) {
      if (el.testIdAttr === 'data-test') {
        return `locator('${this.quoteJsString(`[data-test=${JSON.stringify(testId)}]`)}')`;
      }
      return `getByTestId('${this.quoteJsString(testId)}')`;
    }
    if (role && (text || ariaLabel)) {
      const name = ariaLabel || text;
      return `getByRole('${this.quoteJsString(role)}', { name: '${this.quoteJsString(name)}' })`;
    }
    if (ariaLabel) {
      return `getByLabel('${this.quoteJsString(ariaLabel)}')`;
    }
    if (el.placeholder) {
      return `getByPlaceholder('${this.quoteJsString(el.placeholder)}')`;
    }
    if (el.id) {
      return `locator('${this.quoteJsString(`[id=${JSON.stringify(el.id)}]`)}')`;
    }
    if (el.name) {
      return `locator('${this.quoteJsString(`${el.tag}[name=${JSON.stringify(el.name)}]`)}')`;
    }
    if (text) {
      return text.length < 40
        ? `getByText('${this.quoteJsString(text)}', { exact: true })`
        : `getByText('${this.quoteJsString(text)}')`;
    }
    return el.xpath ? `locator('xpath=${this.quoteJsString(el.xpath)}')` : `locator('${this.quoteJsString(el.tag)}')`;
  }

  private calculateActionCompatibility(tag: string, role: string, action: string, inputType = ''): number {
    const normalizedRole = role.toLowerCase();
    const isClickableInput = tag === 'input'
      && (HeuristicDomMatcher.CLICKABLE_INPUT_TYPES.has(inputType)
        || HeuristicDomMatcher.CLICKABLE_INPUT_TYPES.has(normalizedRole));

    if (action === 'CLICK') {
      if (tag === 'button' || normalizedRole === 'button' || tag === 'a' || normalizedRole === 'link') return 1.0;
      if (isClickableInput || normalizedRole === 'checkbox' || normalizedRole === 'radio'
        || normalizedRole === 'tab' || normalizedRole === 'menuitem') return 1.0;
      return 0.70;
    }
    if (action === 'FILL') {
      if (isClickableInput) return 0.50;
      if (tag === 'input' || tag === 'textarea' || tag === 'select'
        || normalizedRole === 'textbox' || normalizedRole === 'combobox' || normalizedRole === 'searchbox') return 1.0;
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
      if (minLen >= 3 && (minLen / maxLen) >= 0.60) {
        return 0.85;
      }
    }

    // Token overlap
    const tokensA = new Set(a.split(/[\s_\-./]+/).filter(Boolean));
    const tokensB = new Set(b.split(/[\s_\-./]+/).filter(Boolean));
    let intersection = 0;
    for (const t of tokensA) {
      if (tokensB.has(t)) intersection++;
    }
    const overlap = intersection / Math.max(1, Math.max(tokensA.size, tokensB.size));
    return overlap;
  }
}
