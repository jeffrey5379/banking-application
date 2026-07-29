import { test, expect } from "@playwright/test";
import { gotoApp, login, logout, uniqueUsername, OTP_CODE } from "./support/helpers";

// This app's Router uses a NoopLocationStrategy (see support/helpers.ts), so there is no
// deep-linking and no `page.url()` to assert on - every check here is against rendered content.
test.describe("authentication", () => {
  test("an unauthenticated visit lands on the sign-in page", async ({ page }) => {
    await gotoApp(page);
    await expect(page.getByPlaceholder("Enter username")).toBeVisible();
  });

  test("rejects invalid credentials", async ({ page }) => {
    await gotoApp(page);
    await page.getByPlaceholder("Enter username").fill("alice");
    await page.getByPlaceholder("Enter password").fill("wrong-password");
    await page.locator("form").getByRole("button", { name: "Sign In", exact: true }).click();
    await expect(page.locator(".error-banner")).toHaveText("Invalid username or password.");
    // Still on the credentials step, not silently through to the app.
    await expect(page.getByPlaceholder("Enter username")).toBeVisible();
  });

  test("logs in with a valid seeded user through the OTP step, then logs out", async ({ page }) => {
    await login(page, "alice", "alice123");

    await expect(page.locator(".nav-user")).toContainText("alice");

    await logout(page);

    // Session is really gone, not just a client-side route change: a fresh load still
    // lands on sign-in rather than the app remembering alice was ever logged in.
    await gotoApp(page);
    await expect(page.getByPlaceholder("Enter username")).toBeVisible();
  });

  test("rejects a wrong OTP code and allows going back to re-enter credentials", async ({ page }) => {
    await gotoApp(page);
    await page.getByPlaceholder("Enter username").fill("bob");
    await page.getByPlaceholder("Enter password").fill("bob123");
    await page.locator("form").getByRole("button", { name: "Sign In", exact: true }).click();

    await page.getByPlaceholder("6-digit code").fill("000000");
    await page.getByRole("button", { name: "Verify Code", exact: true }).click();
    await expect(page.locator(".error-banner")).toBeVisible();
    await expect(page.getByPlaceholder("6-digit code")).toBeVisible();

    await page.getByRole("button", { name: "← Back" }).click();
    await expect(page.getByPlaceholder("Enter username")).toBeVisible();
  });

  test("registers a brand new user and lands on the accounts page unverified", async ({ page }) => {
    const username = uniqueUsername("e2euser");

    await gotoApp(page);
    await page.getByRole("button", { name: "Register", exact: true }).click();
    await page.getByPlaceholder("Choose a username").fill(username);
    await page.getByPlaceholder("your@email.com").fill(`${username}@example.com`);
    await page.getByPlaceholder("Choose a password").fill("Password1");
    await page.getByRole("button", { name: "Create Account", exact: true }).click();

    await page.getByPlaceholder("6-digit code").fill(OTP_CODE);
    await page.getByRole("button", { name: "Verify Code", exact: true }).click();

    await expect(page.getByRole("heading", { name: "Accounts" })).toBeVisible();
    await expect(page.locator(".nav-user")).toContainText(username);
    // A freshly registered user has no KYC verification yet.
    await expect(page.locator(".kyc-banner")).toBeVisible();
  });
});
