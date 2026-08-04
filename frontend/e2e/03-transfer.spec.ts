import { test, expect } from "@playwright/test";
import {
  getAccountNumber,
  getHeroBalance,
  login,
  logout,
  modalField,
  openAccountByCurrency,
  snapshotAccount,
} from "./support/helpers";

// Runs after 02-accounts.spec.ts on purpose (see the note there) - these tests move real
// money between the real seeded users, so later tests must not assume the untouched
// starting balances.
test.describe("cross-user transfers", () => {
  test("recipient lookup reports found/not found as the fields are filled in", async ({ page }) => {
    await login(page, "alice", "0DxKRQZhD!");
    await openAccountByCurrency(page, "USD");
    await page.getByRole("button", { name: "Send", exact: true }).click();

    await modalField(page, "Amount").fill("10");
    await modalField(page, "Recipient Username").fill("nobody-such-user");
    await modalField(page, "Recipient Account Number").fill("0000000000");
    await expect(page.getByText("Recipient not found")).toBeVisible();

    // A real username paired with someone else's account number must still fail the lookup.
    await modalField(page, "Recipient Username").fill("carol");
    await expect(page.getByText("Recipient not found")).toBeVisible();
  });

  test("alice sends USD to carol and both balances update end to end", async ({ page }) => {
    const carol = await snapshotAccount(page, "carol", "$E3ltbJg^b", "USD");

    await login(page, "alice", "0DxKRQZhD!");
    await openAccountByCurrency(page, "USD");
    const aliceBalanceBefore = await getHeroBalance(page);

    await page.getByRole("button", { name: "Send", exact: true }).click();
    await modalField(page, "Amount").fill("100");
    await modalField(page, "Recipient Username").fill("carol");
    await modalField(page, "Recipient Account Number").fill(carol.accountNumber);
    await expect(page.getByText("Recipient found")).toBeVisible();

    const sendButton = page.locator(".modal-footer").getByRole("button", { name: "Send", exact: true });
    await expect(sendButton).toBeEnabled();
    await sendButton.click();

    // A successful submit closes the modal automatically (account-overview.component.ts).
    await expect(page.locator(".modal-overlay")).toHaveCount(0);
    // toBeCloseTo, not toBe: these are real fractional balances from seed-time currency
    // conversion (see 02-accounts.spec.ts), so exact float equality isn't reliable.
    await expect.poll(() => getHeroBalance(page)).toBeCloseTo(aliceBalanceBefore - 100, 2);

    const firstRow = page.locator(".data-table tbody tr").first();
    await expect(firstRow).toContainText("carol");
    await expect(firstRow).toContainText("100.00");

    await logout(page);
    await login(page, "carol", "$E3ltbJg^b");
    await openAccountByCurrency(page, "USD");
    await expect.poll(() => getHeroBalance(page)).toBeCloseTo(carol.balance + 100, 2);
  });

  test("bob's transfer is blocked by the debit-eligibility check", async ({ page }) => {
    // Seeded per backend/wiremock/mappings/debit-eligibility.json: bob's account ids are
    // denied outright (unlike alice's, which are allowed), so any send from bob must fail
    // closed with this exact server-side message - see AccountService.transferInternal.
    const carol = await snapshotAccount(page, "carol", "$E3ltbJg^b", "USD");

    await login(page, "bob", "jh02EZ3DH#");
    await openAccountByCurrency(page, "EUR");
    const bobBalanceBefore = await getHeroBalance(page);
    const bobAccountNumber = await getAccountNumber(page);

    await page.getByRole("button", { name: "Send", exact: true }).click();
    await modalField(page, "Amount").fill("50");
    await modalField(page, "Recipient Username").fill("carol");
    await modalField(page, "Recipient Account Number").fill(carol.accountNumber);
    await expect(page.getByText("Recipient found")).toBeVisible();

    const sendButton = page.locator(".modal-footer").getByRole("button", { name: "Send", exact: true });
    await expect(sendButton).toBeEnabled();
    await sendButton.click();

    await expect(page.locator(".modal .error-banner")).toHaveText(
      "Debit not allowed. Please contact support for details",
    );
    // A failed submit stays open (see account-overview.component.ts) and resyncs from the
    // server rather than trusting client state, so bob's balance must be untouched.
    await page.getByRole("button", { name: "Cancel" }).click();
    expect(await getHeroBalance(page)).toBe(bobBalanceBefore);
    expect(await getAccountNumber(page)).toBe(bobAccountNumber);

    await logout(page);
    await login(page, "carol", "$E3ltbJg^b");
    await openAccountByCurrency(page, "USD");
    expect(await getHeroBalance(page)).toBe(carol.balance);
  });
});
