import { defineConfig, devices } from '@playwright/test';
import { getServiceConfig, ServiceOS } from '@azure/microsoft-playwright-testing';
import dotenv from 'dotenv';

dotenv.config();

/**
 * Azure Playwright Testing Configuration with Locara Autonomous Healing.
 * Enables massive parallel cloud test execution across 100+ browsers in Microsoft Azure.
 *
 * Requirements:
 * - PLAYWRIGHT_SERVICE_ACCESS_TOKEN or Entra ID credentials
 * - PLAYWRIGHT_SERVICE_URL endpoint
 */
const baseConfig = defineConfig({
  testDir: './tests',
  timeout: 30000,
  expect: {
    timeout: 5000,
  },
  fullyParallel: true,
  retries: 2,
  workers: 20, // Cloud workers on Azure Playwright Testing
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report' }],
    ['json', { outputFile: 'playwright-report/results.json' }]
  ],
  use: {
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],
});

export default defineConfig(
  baseConfig,
  getServiceConfig(baseConfig, {
    exposeNetwork: '<loopback>',
    timeout: 30000,
    os: ServiceOS.LINUX,
    useCloudHostedBrowsers: true
  })
);
