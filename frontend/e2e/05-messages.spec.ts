import { test, expect } from "@playwright/test";
import {
  createMessage,
  getLastToastText,
  getUserId,
  login,
  openMessages,
  toasts,
  unreadBadgeCount,
} from "./support/helpers";

// notification-service's MessageSeeder seeds every demo user (alice/bob/carol) with the exact
// same 3 fixed messages at startup - see that class for the source of truth these assertions are
// pinned to: "Suspicious login attempt detected" (HIGH, unread), "Your card has been issued"
// (NORMAL, unread), "Scheduled maintenance" (NORMAL, already read) - newest first.
//
// Each test below uses a different demo user so they stay independent of both execution order
// and each other: reading a seeded message mutates that user's real backend state (mark-as-read
// isn't reversible from the UI), and pushing a brand new message via /internal/messages adds a
// permanent extra row for whichever user receives it.
test.describe("messages", () => {
  test("seeded messages render correctly, and reading one updates the list and the badge in place", async ({ page }) => {
    await login(page, "carol", "$E3ltbJg^b");

    // 2 of the 3 seeded messages start unread.
    await expect(page.locator(".unread-badge")).toHaveText("2");

    await openMessages(page);

    const rows = page.locator(".message-row");
    await expect(rows).toHaveCount(3);

    // Newest first.
    await expect(rows.nth(0)).toContainText("Suspicious login attempt detected");
    await expect(rows.nth(1)).toContainText("Your card has been issued");
    await expect(rows.nth(2)).toContainText("Scheduled maintenance");

    // The two unread ones are bold (class "unread"); the pre-read one isn't.
    await expect(rows.nth(0)).toHaveClass(/unread/);
    await expect(rows.nth(1)).toHaveClass(/unread/);
    await expect(rows.nth(2)).not.toHaveClass(/unread/);

    // Only the HIGH-priority message gets the "Important" badge.
    await expect(rows.nth(0).locator(".badge-debit")).toHaveText("Important");
    await expect(rows.nth(1).locator(".badge-debit")).toHaveCount(0);
    await expect(rows.nth(2).locator(".badge-debit")).toHaveCount(0);

    // Opening the unread, high-priority message shows its full content...
    await rows.nth(0).click();
    await expect(page.locator(".modal-header h3")).toHaveText("Suspicious login attempt detected");
    await expect(page.locator(".message-detail-body")).toContainText(
      "We detected a login attempt to your account from a new device.",
    );
    await expect(page.locator(".modal-body .badge-debit")).toHaveText("Important");
    await page.locator(".modal-close").click();
    await expect(page.locator(".modal-overlay")).toHaveCount(0);

    // ...and marks it read - both the row and the topbar badge update without any reload.
    await expect(rows.nth(0)).not.toHaveClass(/unread/);
    await expect(page.locator(".unread-badge")).toHaveText("1");

    // Opening the already-read seeded message just shows it - no further state change.
    await rows.nth(2).click();
    await expect(page.locator(".message-detail-body")).toContainText("scheduled maintenance");
    await page.locator(".modal-close").click();
    await expect(page.locator(".unread-badge")).toHaveText("1");

    await page.locator(".back-link").click();
    await expect(page.getByRole("heading", { name: "Accounts" })).toBeVisible();
  });

  test("a message pushed while logged in shows a toast and lands at the top of the list unread", async ({ page, request }) => {
    await login(page, "bob", "jh02EZ3DH#");
    const bobId = await getUserId(page);
    const before = await unreadBadgeCount(page);

    await createMessage(request, {
      ownerId: bobId,
      subject: "Wire transfer received",
      body: "A wire transfer of 500.00 EUR has been credited to your account.",
      priority: "HIGH",
    });

    // Nothing on this page is reloaded or re-clicked before these checks - both the toast and
    // the badge arrive purely from the live SSE push (see notification.service.ts).
    await expect(toasts(page)).toHaveCount(1);
    const toastText = await getLastToastText(page);
    expect(toastText).toContain("You have a new message:");
    expect(toastText).toContain("Wire transfer received");

    await expect.poll(() => unreadBadgeCount(page)).toBe(before + 1);

    await openMessages(page);
    const firstRow = page.locator(".message-row").first();
    await expect(firstRow).toContainText("Wire transfer received");
    await expect(firstRow).toHaveClass(/unread/);
    await expect(firstRow.locator(".badge-debit")).toHaveText("Important");

    await firstRow.click();
    await expect(page.locator(".modal-header h3")).toHaveText("Wire transfer received");
    await expect(page.locator(".message-detail-body")).toContainText(
      "A wire transfer of 500.00 EUR has been credited to your account.",
    );
    await page.locator(".modal-close").click();

    await expect(firstRow).not.toHaveClass(/unread/);
    await expect.poll(() => unreadBadgeCount(page)).toBe(before);
  });

  test("a message pushed with no priority defaults to NORMAL and gets no Important badge", async ({ page, request }) => {
    await login(page, "alice", "0DxKRQZhD!");
    const aliceId = await getUserId(page);

    await createMessage(request, {
      ownerId: aliceId,
      subject: "Scheduled maintenance reminder",
      body: "A reminder that scheduled maintenance begins tonight at 01:00 CET.",
      // priority omitted on purpose - CreateMessageRequest.priorityOrDefault() should apply.
    });

    await expect(toasts(page)).toHaveCount(1);
    expect(await getLastToastText(page)).toContain("Scheduled maintenance reminder");

    await openMessages(page);
    const firstRow = page.locator(".message-row").first();
    await expect(firstRow).toContainText("Scheduled maintenance reminder");
    await expect(firstRow.locator(".badge-debit")).toHaveCount(0);
  });

  test("a message pushed for someone else never shows up as this user's toast or message", async ({ page, request }) => {
    await login(page, "carol", "$E3ltbJg^b");
    const before = await unreadBadgeCount(page);

    await createMessage(request, {
      ownerId: "00000000-0000-0000-0000-000000000000",
      subject: "Not for carol",
      body: "This message belongs to a different user entirely.",
    });

    // No toast, no badge change - filtered out server-side (NotificationController.stream()
    // filters both event types by ownerId before they ever reach the SSE response).
    await page.waitForTimeout(1000);
    await expect(toasts(page)).toHaveCount(0);
    expect(await unreadBadgeCount(page)).toBe(before);
  });
});
