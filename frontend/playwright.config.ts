import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  // Resets core-banking's/notification-service's backend state before every run
  globalSetup: "./e2e/global-setup.ts",
  fullyParallel: false,
  // Tests exercise shared, real backend state (seeded users' account
  // balances) rather than mocks, so they must not interleave.
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [["html", { open: "never" }]],
  timeout: 30_000,
  // Higher than Playwright's 5s default: this suite hits a real, Redis-backed rate limiter
  // on the gateway (see gateway-service/application.yml), so a burst of real requests can
  // occasionally need more time than a mocked backend ever would.
  expect: { timeout: 10_000 },
  use: {
    baseURL: "http://localhost:4200",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  webServer: {
    command: "npm run start:e2e",
    url: "http://localhost:4200",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
