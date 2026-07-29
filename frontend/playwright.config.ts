import { defineConfig, devices } from "@playwright/test";

// These UI tests drive the real app end to end (real login/OTP, real
// transfers between seeded users) against the backend stack started via
// ../docker-compose.e2e.yml - see README "UI tests (Playwright)". Only
// Angular's own dev server is started here (via the "e2e" build configuration -
// see angular.json/environment.e2e.ts - which shortens the SSE proactive-renewal
// interval so 04-balance-updates.spec.ts doesn't have to wait out the real one);
// the gateway and every service behind it must already be running on their usual
// localhost ports.
export default defineConfig({
  testDir: "./e2e",
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
