/**
 * Shared Danger & Financial Polarity Guard for Playwright DOM and MCP snapshot matchers.
 * Keeps destructive and financial transaction isolation rules synchronized across all web adapters.
 */

export const DANGER_KEYWORDS: readonly string[] = [
  'batal', 'batalkan', 'cancel', 'hapus', 'delete', 'remove', 'blokir', 'block',
  'tolak', 'reject', 'decline', 'tutup akun', 'close account', 'logout', 'keluar', 'sign out'
];

export const FINANCIAL_KEYWORDS: readonly string[] = [
  'bayar', 'pembayaran', 'pay', 'payment', 'transfer', 'kirim uang', 'checkout',
  'topup', 'top up', 'isi ulang', 'beli', 'pembelian', 'tarik tunai', 'withdraw', 'debet', 'autodebit',
  'subscribe', 'subscription', 'langganan', 'penambahan unit',
  'redeem', 'redemption', 'cairkan', 'pencairan', 'jual unit',
  'switching', 'pengalihan'
];

export function hasPolarityKeyword(input: string, keywords: readonly string[]): boolean {
  if (!input) return false;
  const normalized = input.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
  if (!normalized) return false;
  const padded = ` ${normalized} `;
  const tokens = new Set(normalized.split(' ').filter(Boolean));
  return keywords.some(kw => (kw.includes(' ') ? padded.includes(` ${kw} `) : tokens.has(kw)));
}

export function isPolarityViolation(targetHint: string, ...candidateFields: string[]): boolean {
  if (!targetHint) return false;
  const combined = candidateFields.filter(Boolean).join(' ');
  if (!combined) return false;

  const targetDanger = hasPolarityKeyword(targetHint, DANGER_KEYWORDS);
  const nodeDanger = hasPolarityKeyword(combined, DANGER_KEYWORDS);
  if (targetDanger !== nodeDanger) {
    return true;
  }

  const targetFinancial = hasPolarityKeyword(targetHint, FINANCIAL_KEYWORDS);
  const nodeFinancial = hasPolarityKeyword(combined, FINANCIAL_KEYWORDS);
  if (!targetFinancial && nodeFinancial) {
    return true;
  }

  return false;
}
