/**
 * Locara for Cucumber BDD (TypeScript / JavaScript).
 * Universal self-healing step definitions compatible with @cucumber/cucumber,
 * Playwright, and WebdriverIO.
 */

import { When, Then } from '@cucumber/cucumber';

// Generic world context type representing active driver/page
export interface LocaraCucumberWorld {
  page?: any;
  driver?: any;
  locara?: any;
}

export function toXPathLiteral(value: string): string {
  if (!value.includes("'")) {
    return `'${value}'`;
  }
  if (!value.includes('"')) {
    return `"${value}"`;
  }
  const parts = value.split("'").map((part) => `'${part}'`);
  return `concat(${parts.join(', "\'", ')})`;
}

/**
 * Universal Click / Tap Step Definition with Autonomous Healing.
 * Matches:
 *   When User clicks on "Bayar Sekarang"
 *   And Pengguna menekan tombol "Lanjut Transfer"
 */
When(/(?:User clicks on|Pengguna menekan tombol|User taps on|Pengguna memilih)\s+"([^"]+)"/, async function (this: LocaraCucumberWorld, targetHint: string) {
  if (this.locara && this.page) {
    await this.locara.click(targetHint, targetHint);
  } else if (this.page) {
    // Direct Playwright healing lookup
    const btn = this.page.getByRole('button', { name: targetHint }).or(this.page.getByText(targetHint));
    await btn.click({ timeout: 5000 });
  } else if (this.driver) {
    // Appium mobile driver
    const lit = toXPathLiteral(targetHint);
    const el = await this.driver.$(`//*[@text=${lit} or @content-desc=${lit}]`);
    await el.click();
  }
});

/**
 * Universal Input / Fill Step Definition with Autonomous Healing.
 * Matches:
 *   When User fills "123456" into "M-PIN"
 *   And Pengguna memasukkan "mega_user_01" pada field "Username"
 */
When(/(?:User fills|Pengguna memasukkan|User types|Pengguna mengisi)\s+"([^"]+)"\s+(?:into|pada field|into field|pada kolom)\s+"([^"]+)"/, async function (this: LocaraCucumberWorld, value: string, targetHint: string) {
  if (this.locara && this.page) {
    await this.locara.fill(targetHint, value, targetHint);
  } else if (this.page) {
    const input = this.page.getByLabel(targetHint).or(this.page.getByPlaceholder(targetHint)).or(this.page.getByTestId(targetHint));
    await input.fill(value, { timeout: 5000 });
  } else if (this.driver) {
    const lit = toXPathLiteral(targetHint);
    const el = await this.driver.$(`//*[@hint=${lit} or @text=${lit} or contains(@resource-id, ${lit})]`);
    await el.setValue(value);
  }
});

/**
 * Universal Verification / Assert Step Definition with Autonomous Healing.
 * Matches:
 *   Then User verifies "Transaksi Berhasil" is visible
 *   Then Pengguna melihat status "Berhasil"
 */
Then(/(?:User verifies|Pengguna melihat status|User should see|Pengguna memastikan)\s+"([^"]+)"(?:\s+is visible|\s+tampil)?/, async function (this: LocaraCucumberWorld, targetHint: string) {
  if (this.locara && this.page) {
    const el = await this.locara.locator(targetHint, targetHint);
    await el.waitFor({ state: 'visible', timeout: 5000 });
  } else if (this.page) {
    const el = this.page.getByText(targetHint);
    await el.waitFor({ state: 'visible', timeout: 5000 });
  } else if (this.driver) {
    const lit = toXPathLiteral(targetHint);
    const el = await this.driver.$(`//*[@text=${lit}]`);
    await el.waitForDisplayed({ timeout: 5000 });
  }
});
