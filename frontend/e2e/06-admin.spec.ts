import { test, expect, Page } from "@playwright/test";
import {
  getLastToastText,
  gotoApp,
  login,
  loginAdmin,
  logout,
  modalField,
  openMessages,
  registerUser,
  toasts,
  uniqueUsername,
  unreadBadgeCount,
} from "./support/helpers";

const ADMIN_USERNAME = "admin";
const ADMIN_PASSWORD = "dIO1x$AN^z";

function userRow(page: Page, username: string) {
  return page.locator(".data-table tbody tr").filter({ hasText: username });
}

test.describe("admin console", () => {
  test("existing seeded users show their real, already-verified KYC status", async ({ page }) => {
    await loginAdmin(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    // DataSeeder runs alice/bob/carol through submitIdentity() + both documents at startup, so
    // all three are VERIFIED from the moment they exist - this is a read-only sanity check, not
    // a state mutation, so it's safe to run against the shared seeded accounts.
    for (const username of ["alice", "bob", "carol"]) {
      const badge = userRow(page, username).locator(".badge");
      await expect(badge).toHaveText("Verified");
      await expect(badge).toHaveClass(/badge-success/);
    }
  });

  test("a freshly registered user shows Not started, and stays Not started after submitting identity details alone", async ({ page }) => {
    const username = uniqueUsername("e2ekyc");
    const password = "Password1!";
    await registerUser(page, username, password);
    await logout(page);

    await loginAdmin(page, ADMIN_USERNAME, ADMIN_PASSWORD);
    const badge = userRow(page, username).locator(".badge");
    await expect(badge).toHaveText("Not started");
    await expect(badge).toHaveClass(/badge-muted/);
    await logout(page);

    // Submitting identity details alone must NOT move the profile to PENDING
    await login(page, username, password);
    await page.locator(".kyc-banner").click();
    await expect(page.getByRole("heading", { name: "Identity Verification" })).toBeVisible();
    await page.getByPlaceholder("Enter your first name").fill("Test");
    await page.getByPlaceholder("Enter your last name").fill("User");
    await page.getByPlaceholder("ID card or passport number").fill("TU1234567");
    await page.getByRole("button", { name: "Continue", exact: true }).click();
    // The upload-documents step only renders once the identity submission actually round-tripped
    // through the backend (hasSubmittedIdentity() reads status().firstName from the response).
    await expect(page.getByRole("heading", { name: "Upload documents", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Back" }).click();
    await expect(page.getByRole("heading", { name: "Accounts" })).toBeVisible();
    await logout(page);

    await loginAdmin(page, ADMIN_USERNAME, ADMIN_PASSWORD);
    await expect(badge).toHaveText("Not started");
    await expect(badge).toHaveClass(/badge-muted/);
  });

  test("deactivating a user immediately blocks login, and reactivating restores it", async ({ page }) => {
    const username = uniqueUsername("e2edeactivate");
    const password = "Password1!";
    await registerUser(page, username, password);
    await logout(page);

    await loginAdmin(page, ADMIN_USERNAME, ADMIN_PASSWORD);
    const row = userRow(page, username);
    const toggle = row.locator(".switch");
    await expect(toggle).toHaveClass(/switch-on/);

    await toggle.click();
    await expect(page.locator(".modal-header h3")).toHaveText("Deactivate user");
    await page.locator(".modal-footer").getByRole("button", { name: "Confirm", exact: true }).click();
    await expect(page.locator(".modal-overlay")).toHaveCount(0);
    await expect(toggle).not.toHaveClass(/switch-on/);
    await logout(page);

    // A disabled account is rejected by authenticationManager.authenticate() before any OTP
    // challenge is even issued (AuthService.login()), so this never reaches the OTP step.
    await gotoApp(page);
    await page.getByPlaceholder("Enter username").fill(username);
    await page.getByPlaceholder("Enter password").fill(password);
    await page.locator("form").getByRole("button", { name: "Sign In", exact: true }).click();
    await expect(page.locator(".error-banner")).toHaveText(
      "This account has been deactivated. Please contact support for details.",
    );
    await expect(page.getByPlaceholder("Enter username")).toBeVisible();
    await expect(page.getByPlaceholder("6-digit code")).toHaveCount(0);

    // The toggle isn't a one-way ban - reactivating restores a normal login.
    await loginAdmin(page, ADMIN_USERNAME, ADMIN_PASSWORD);
    await expect(toggle).not.toHaveClass(/switch-on/);
    await toggle.click();
    await expect(page.locator(".modal-header h3")).toHaveText("Activate user");
    await page.locator(".modal-footer").getByRole("button", { name: "Confirm", exact: true }).click();
    await expect(toggle).toHaveClass(/switch-on/);
    await logout(page);

    await login(page, username, password);
  });

  test("sending a message from the admin console delivers it live, and the modal only closes via the × button", async ({ browser }) => {
    const carolContext = await browser.newContext();
    const carolPage = await carolContext.newPage();
    await login(carolPage, "carol", "$E3ltbJg^b");
    const before = await unreadBadgeCount(carolPage);

    const adminContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    await loginAdmin(adminPage, ADMIN_USERNAME, ADMIN_PASSWORD);

    await userRow(adminPage, "carol").locator('.btn-icon[title="Send message"]').click();
    await expect(adminPage.locator(".modal-header h3")).toHaveText("Send message to carol");

    // Clicking the backdrop must not close the modal - only the × button does (recent change).
    // Clicked near a corner, since the overlay is a centered flex container and the modal itself
    // would otherwise absorb a click aimed at its middle.
    await adminPage.locator(".modal-overlay").click({ position: { x: 10, y: 10 } });
    await expect(adminPage.locator(".modal-overlay")).toHaveCount(1);

    await modalField(adminPage, "Subject").fill("Action required on your account");
    await modalField(adminPage, "Body").fill("Please review your recent account activity at your earliest convenience.");
    await modalField(adminPage, "Priority").selectOption("HIGH");
    await adminPage.locator(".modal-footer").getByRole("button", { name: "Send", exact: true }).click();
    await expect(adminPage.locator(".modal-overlay")).toHaveCount(0);

    // Nothing on carolPage is reloaded or re-clicked before these checks - both the toast and the
    // badge arrive purely from the live SSE push, exactly as with a direct /internal/messages push.
    await expect(toasts(carolPage)).toHaveCount(1);
    const toastText = await getLastToastText(carolPage);
    expect(toastText).toContain("You have a new message:");
    expect(toastText).toContain("Action required on your account");
    await expect.poll(() => unreadBadgeCount(carolPage)).toBe(before + 1);

    await openMessages(carolPage);
    const firstRow = carolPage.locator(".message-row").first();
    await expect(firstRow).toContainText("Action required on your account");
    await expect(firstRow).toHaveClass(/unread/);
    await expect(firstRow.locator(".badge-debit")).toHaveText("Important");

    await firstRow.click();
    await expect(carolPage.locator(".modal-header h3")).toHaveText("Action required on your account");
    await expect(carolPage.locator(".message-detail-body")).toContainText(
      "Please review your recent account activity at your earliest convenience.",
    );
    await carolPage.locator(".modal-close").click();

    await adminContext.close();
    await carolContext.close();
  });
});
