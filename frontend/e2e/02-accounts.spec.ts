import { test, expect } from "@playwright/test";
import { accountCardByCurrency, getAccountNumber, getHeroBalance, login } from "./support/helpers";

// Numeric file prefixes (01-, 02-, 03-...) enforce run order: these tests read seeded
// balances that 03-transfer.spec.ts intentionally mutates, so they must run first
// against the real, shared backend (playwright.config.ts runs everything with 1 worker).
//
// DataSeeder funds every demo account from the bank's EUR reserve, and AccountService
// converts that EUR amount into the target account's own currency at the seeded exchange
// rate (USD 0.92, GBP 1.17 per EUR) - so e.g. carol's "7000.00 EUR" seed becomes a real
// balance of 7,608.70 USD, not a round 7,000.00. These expected values are that conversion
// applied, not arbitrary numbers.
test.describe("accounts", () => {
  test("lists a seeded user's accounts with real balances", async ({ page }) => {
    await login(page, "carol", "$E3ltbJg^b");

    await expect(accountCardByCurrency(page, "USD")).toBeVisible();
    await expect(accountCardByCurrency(page, "USD").locator(".balance-amount")).toHaveText("7,608.70");
    // Single-currency user, so the summary pill total matches the one account.
    await expect(page.locator(".summary-pill")).toContainText("7,608.70");
  });

  test("opens an account and shows its detail page", async ({ page }) => {
    await login(page, "carol", "$E3ltbJg^b");
    await accountCardByCurrency(page, "USD").click();

    await expect(page.getByText("Current balance")).toBeVisible();
    expect(await getHeroBalance(page)).toBeCloseTo(7608.7, 2);
    expect(await getAccountNumber(page)).toMatch(/\d+/);
    await expect(page.getByRole("button", { name: "Exchange", exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Send", exact: true })).toBeVisible();
  });

  test("a user with multiple accounts sees every one of them", async ({ page }) => {
    await login(page, "alice", "0DxKRQZhD!");

    await expect(accountCardByCurrency(page, "EUR")).toBeVisible();
    await expect(accountCardByCurrency(page, "USD")).toBeVisible();
    await expect(accountCardByCurrency(page, "GBP")).toBeVisible();
    await expect(accountCardByCurrency(page, "USD").locator(".balance-amount")).toHaveText("3,260.87");
    await expect(accountCardByCurrency(page, "GBP").locator(".balance-amount")).toHaveText("1,709.40");
  });
});
