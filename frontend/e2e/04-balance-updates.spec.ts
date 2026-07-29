import { test, expect } from "@playwright/test";
import {
  fillSendMoneyForm,
  getAccountNumber,
  getHeroBalance,
  getLastToastText,
  login,
  logout,
  openAccountByCurrency,
  sendMoney,
  submitSendMoney,
  toasts,
} from "./support/helpers";

// Runs after 03-transfer.spec.ts on purpose (same file-ordering reasoning as that file):
// test 1 below relies on bob's EUR account still having only its single seeded operation
// (no chart yet), which only holds if nothing earlier in the suite has transacted on it.
//
// Each test needs two independent, simultaneously-logged-in sessions - bob passively sitting
// on his own account page (receiving live SSE pushes), and alice actively sending him money
// from a *separate browser context* (not just another tab/page: session storage and the SSE
// connection itself must be genuinely isolated per user, the way two different people's
// browsers would be). Nothing on bob's page is ever reloaded or re-clicked after alice's send -
// every assertion below is only true if the update really arrived over the live SSE stream
// (see notification.service.ts / account-detail.effects.ts's refreshOnLiveBalanceUpdate$).
test.describe("live balance updates (SSE)", () => {
  test("balance, balance history, transaction history and a toast all update live", async ({ browser }) => {
    const bobContext = await browser.newContext();
    const bobPage = await bobContext.newPage();
    await login(bobPage, "bob", "bob123");
    await openAccountByCurrency(bobPage, "EUR");

    const bobAccountNumber = await getAccountNumber(bobPage);
    const balanceBefore = await getHeroBalance(bobPage);
    const rowsBefore = await bobPage.locator(".data-table tbody tr").count();
    // Only the single seeded "Initial deposit" op exists yet - the chart needs >= 2 points.
    await expect(bobPage.locator(".chart-card")).toHaveCount(0);
    // Nobody has sent bob anything yet in this fresh run.
    await expect(toasts(bobPage)).toHaveCount(0);

    const aliceContext = await browser.newContext();
    const alicePage = await aliceContext.newPage();
    await login(alicePage, "alice", "alice123");
    // Alice's EUR account (debit-eligible per wiremock's seeded account ids - see
    // AccountService.transferInternal) hasn't been used as a sender anywhere else in the suite.
    await openAccountByCurrency(alicePage, "EUR");
    await sendMoney(alicePage, {
      amount: "25",
      recipientUsername: "bob",
      recipientAccountNumber: bobAccountNumber,
      description: "sse live-update check",
    });

    // Nothing below touches bobPage except reading it - all four checks land purely from the
    // live SSE push while bob's page just sits there.
    await expect.poll(() => getHeroBalance(bobPage)).toBeCloseTo(balanceBefore + 25, 2);
    await expect(bobPage.locator(".chart-card")).toBeVisible();
    await expect(bobPage.locator(".data-table tbody tr")).toHaveCount(rowsBefore + 1);
    const firstRow = bobPage.locator(".data-table tbody tr").first();
    await expect(firstRow).toContainText("alice");
    await expect(firstRow).toContainText("25.00");

    await expect(toasts(bobPage)).toHaveCount(1);
    const toastText = await getLastToastText(bobPage);
    expect(toastText).toContain(bobAccountNumber);
    expect(toastText).toContain("balance changed");
    // Toast text interpolates the raw event amount with no currency-pipe formatting (see
    // notification.service.ts) - "+25 EUR", not "+25.00 EUR" like the transaction table.
    expect(toastText).toContain("+25 EUR");
    expect(toastText).toContain("sse live-update check");

    await aliceContext.close();
    await bobContext.close();
  });

  test("still delivers live updates after the SSE connection renews itself on its own timeout", async ({ browser }) => {
    // Real proactive renewal is every 4 minutes (MAX_CONNECTION_AGE_MS) in production - too slow
    // for a test. This whole suite instead runs against the "e2e" Angular build configuration
    // (environment.e2e.ts, see angular.json), which shortens it to 20s, so this test's own
    // budget needs enough headroom to actually wait one out.
    test.setTimeout(90_000);
    const bobContext = await browser.newContext();
    const bobPage = await bobContext.newPage();

    const ticketRequests: string[] = [];
    bobPage.on("request", (req) => {
      if (req.url().includes("/api/notifications/ticket")) ticketRequests.push(req.url());
    });

    await login(bobPage, "bob", "bob123");
    await openAccountByCurrency(bobPage, "EUR");
    const bobAccountNumber = await getAccountNumber(bobPage);
    const ticketCountAfterLogin = ticketRequests.length; // the initial connect()'s own ticket

    // Get alice all the way up to "ready to click Send" while the renewal timer is still
    // running, so the only thing left to do once the renewal is confirmed is the click itself.
    const aliceContext = await browser.newContext();
    const alicePage = await aliceContext.newPage();
    await login(alicePage, "alice", "alice123");
    await openAccountByCurrency(alicePage, "EUR");
    await fillSendMoneyForm(alicePage, {
      amount: "10",
      recipientUsername: "bob",
      recipientAccountNumber: bobAccountNumber,
      description: "after sse renewal",
    });

    // Wait for an actual renewal - a second real ticket fetch - not just for time to pass;
    // that request is the mechanism under test, not an implementation detail to skip past. Timeout
    // comfortably above the 20s e2e renewal interval, since some of it has already elapsed doing
    // bob's/alice's setup above.
    await expect.poll(() => ticketRequests.length, { timeout: 30_000 }).toBeGreaterThan(ticketCountAfterLogin);
    // Brief settle for the new EventSource to finish connecting server-side.
    await bobPage.waitForTimeout(300);

    const balanceBefore = await getHeroBalance(bobPage);
    const rowsBefore = await bobPage.locator(".data-table tbody tr").count();

    await submitSendMoney(alicePage);

    await expect.poll(() => getHeroBalance(bobPage)).toBeCloseTo(balanceBefore + 10, 2);
    await expect(bobPage.locator(".chart-card")).toBeVisible();
    await expect(bobPage.locator(".data-table tbody tr")).toHaveCount(rowsBefore + 1);
    await expect(bobPage.locator(".data-table tbody tr").first()).toContainText("alice");
    await expect(toasts(bobPage).last()).toBeVisible();
    expect(await getLastToastText(bobPage)).toContain("+10 EUR");

    await aliceContext.close();
    await bobContext.close();
  });

  test("still delivers live updates after logging out and back in", async ({ browser }) => {
    test.setTimeout(60_000);
    const bobContext = await browser.newContext();
    const bobPage = await bobContext.newPage();

    const ticketRequests: string[] = [];
    bobPage.on("request", (req) => {
      if (req.url().includes("/api/notifications/ticket")) ticketRequests.push(req.url());
    });

    await login(bobPage, "bob", "bob123");
    await openAccountByCurrency(bobPage, "EUR");
    const bobAccountNumber = await getAccountNumber(bobPage);
    const ticketCountBeforeRelogin = ticketRequests.length;

    // logout() disconnects the SSE stream (AppComponent.onLoggedOut); logging back in
    // reconnects it fresh via a brand-new ticket (AppComponent's router-driven
    // syncNotificationConnection - see its NavigationEnd subscription).
    await logout(bobPage);
    await login(bobPage, "bob", "bob123");
    // A brief settle before clicking: this is a *re*-login in the same long-running SPA
    // instance (NoopLocationStrategy means no page reload), and the accounts list re-fetching
    // right on top of the just-rendered one from the first login can otherwise detach the very
    // card being clicked mid-click.
    await bobPage.waitForTimeout(500);
    await openAccountByCurrency(bobPage, "EUR");

    // Confirm the reconnect actually happened (a real new ticket request), not just that time
    // passed - this is the mechanism the test is meant to exercise.
    await expect.poll(() => ticketRequests.length).toBeGreaterThan(ticketCountBeforeRelogin);

    const aliceContext = await browser.newContext();
    const alicePage = await aliceContext.newPage();
    await login(alicePage, "alice", "alice123");
    await openAccountByCurrency(alicePage, "EUR");
    await fillSendMoneyForm(alicePage, {
      amount: "15",
      recipientUsername: "bob",
      recipientAccountNumber: bobAccountNumber,
      description: "after relogin",
    });

    // Brief settle for the fresh post-login EventSource to finish connecting server-side.
    await bobPage.waitForTimeout(300);
    const balanceBefore = await getHeroBalance(bobPage);
    const rowsBefore = await bobPage.locator(".data-table tbody tr").count();

    await submitSendMoney(alicePage);

    await expect.poll(() => getHeroBalance(bobPage)).toBeCloseTo(balanceBefore + 15, 2);
    await expect(bobPage.locator(".chart-card")).toBeVisible();
    await expect(bobPage.locator(".data-table tbody tr")).toHaveCount(rowsBefore + 1);
    await expect(bobPage.locator(".data-table tbody tr").first()).toContainText("alice");
    await expect(toasts(bobPage).last()).toBeVisible();
    expect(await getLastToastText(bobPage)).toContain("+15 EUR");

    await aliceContext.close();
    await bobContext.close();
  });
});
