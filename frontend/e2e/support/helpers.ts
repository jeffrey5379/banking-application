import { APIRequestContext, Page, expect } from "@playwright/test";
import { createHmac } from "node:crypto";

export const OTP_CODE = "111111";

export async function gotoApp(page: Page) {
  await page.goto("/");
}

export async function login(page: Page, username: string, password: string) {
  await gotoApp(page);
  await page.getByPlaceholder("Enter username").fill(username);
  await page.getByPlaceholder("Enter password").fill(password);
  // Scoped to the form: the "Sign In" tab button above it has the same accessible name.
  await page.locator("form").getByRole("button", { name: "Sign In", exact: true }).click();
  await page.getByPlaceholder("6-digit code").fill(OTP_CODE);
  await page.getByRole("button", { name: "Verify Code", exact: true }).click();
  await page.waitForTimeout(500);
  await expect(page.getByRole("heading", { name: "Accounts" })).toBeVisible();
  await expect(page.locator(".account-card, .empty-state").first()).toBeVisible();
}

export async function logout(page: Page) {
  await page.locator(".btn-logout").click();
  await expect(page.getByPlaceholder("Enter username")).toBeVisible();
}

// Unique enough per test run to never collide with seeded users or a
// previous run against the same long-lived backend.
export function uniqueUsername(prefix: string): string {
  return `${prefix}${Date.now()}${Math.floor(Math.random() * 1000)}`;
}

export function parseMoney(text: string): number {
  return Number(text.replace(/[^0-9.-]/g, ""));
}

export function accountCardByCurrency(page: Page, currency: string) {
  return page.locator(`.account-card:has(.currency-${currency.toLowerCase()})`);
}

export async function openAccountByCurrency(page: Page, currency: string) {
  await accountCardByCurrency(page, currency).click();
  await expect(page.getByText("Current balance")).toBeVisible();
  await waitForTransactionsLoaded(page);
}

// The transactions table only renders once `transactions()` has actually loaded (see
// account-overview.component.ts's `@if`) - reading `.data-table tbody tr`'s count right after
// the hero card appears can race that load and see 0 rows instead of the real starting count.
export async function waitForTransactionsLoaded(page: Page): Promise<void> {
  await expect(page.locator(".data-table tbody tr, .empty-state").first()).toBeVisible();
}

export async function getHeroBalance(page: Page): Promise<number> {
  return parseMoney(await page.locator(".hero-amount").innerText());
}

export async function getAccountNumber(page: Page): Promise<string> {
  // The "Account 1234567890" line is the only `.monospace` element under the hero card
  // (account-overview.component.ts) - more robust than text-matching around whitespace.
  const text = await page.locator(".hero-left .monospace").innerText();
  return text.replace("Account", "").trim();
}

export function modalField(page: Page, labelText: string) {
  return page.locator(".modal .form-group").filter({ hasText: labelText }).locator("input");
}

export interface AccountSnapshot {
  accountNumber: string;
  balance: number;
}

export interface SendMoneyOptions {
  amount: string;
  recipientUsername: string;
  recipientAccountNumber: string;
  description?: string;
}

export async function fillSendMoneyForm(page: Page, opts: SendMoneyOptions): Promise<void> {
  await page.getByRole("button", { name: "Send", exact: true }).click();
  await modalField(page, "Amount").fill(opts.amount);
  await modalField(page, "Recipient Username").fill(opts.recipientUsername);
  await modalField(page, "Recipient Account Number").fill(opts.recipientAccountNumber);
  if (opts.description) {
    await modalField(page, "Description").fill(opts.description);
  }
  await expect(page.getByText("Recipient found")).toBeVisible();
  await expect(page.locator(".modal-footer").getByRole("button", { name: "Send", exact: true })).toBeEnabled();
}

export async function submitSendMoney(page: Page): Promise<void> {
  await page.locator(".modal-footer").getByRole("button", { name: "Send", exact: true }).click();
  await expect(page.locator(".modal-overlay")).toHaveCount(0);
}

export async function sendMoney(page: Page, opts: SendMoneyOptions): Promise<void> {
  await fillSendMoneyForm(page, opts);
  await submitSendMoney(page);
}

export function toasts(page: Page) {
  return page.locator(".toast");
}

export async function getLastToastText(page: Page): Promise<string> {
  return (await toasts(page).last().innerText()).replace(/\s+/g, " ").trim();
}

// Logs in as the given user just long enough to read one account's real, current
// number/balance off the UI, then logs back out - used to seed a transfer test's
// "recipient" side without hardcoding account numbers.
export async function snapshotAccount(
  page: Page,
  username: string,
  password: string,
  currency: string,
): Promise<AccountSnapshot> {
  await login(page, username, password);
  await openAccountByCurrency(page, currency);
  const balance = await getHeroBalance(page);
  const accountNumber = await getAccountNumber(page);
  await logout(page);
  return { accountNumber, balance };
}

// ── Messages ────────────────────────────────────────────────────────────

// auth.service.ts stores this straight off the login response - the only place a logged-in
// user's raw UUID is available to a test, since the UI itself only ever shows public-facing
// account numbers/usernames (see "Hide internal DB ids behind public_id UUIDs").
export async function getUserId(page: Page): Promise<string> {
  const raw = await page.evaluate(() => sessionStorage.getItem("auth_user"));
  if (!raw) {
    throw new Error("getUserId() called before login - no auth_user in sessionStorage");
  }
  return JSON.parse(raw).userId;
}

const NOTIFICATION_SERVICE_URL = "http://localhost:8083";

export interface CreateMessageOptions {
  ownerId: string;
  subject: string;
  body: string;
  priority?: "NORMAL" | "HIGH";
}

const CORE_BANKING_DEV_SERVICE_SECRET_BASE64 = "zacTVpF/cO45cVpOAYTHbF0Rzntz1viiftAiVgBDJhDlRuWIfazQce/nKEJBDc5f";

function base64url(input: Buffer): string {
  return input.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function mintCoreBankingServiceToken(): string {
  const header = { alg: "HS256", kid: "core-banking" };
  const nowSeconds = Math.floor(Date.now() / 1000);
  const payload = { scope: "internal", iat: nowSeconds, exp: nowSeconds + 60 };

  const signingInput = `${base64url(Buffer.from(JSON.stringify(header)))}.${base64url(Buffer.from(JSON.stringify(payload)))}`;
  const secretBytes = Buffer.from(CORE_BANKING_DEV_SERVICE_SECRET_BASE64, "base64");
  const signature = createHmac("sha256", secretBytes).update(signingInput).digest();

  return `${signingInput}.${base64url(signature)}`;
}

export async function createMessage(request: APIRequestContext, opts: CreateMessageOptions): Promise<void> {
  const res = await request.post(`${NOTIFICATION_SERVICE_URL}/internal/messages`, {
    headers: { "X-Service-Token": mintCoreBankingServiceToken() },
    data: {
      ownerId: opts.ownerId,
      subject: opts.subject,
      body: opts.body,
      priority: opts.priority,
    },
  });
  if (!res.ok()) {
    throw new Error(`POST /internal/messages failed: ${res.status()} ${await res.text()}`);
  }
}

// Clicks the envelope icon in the topbar (visible on every page once logged in) and waits for
// the Messages page's own content to be ready - mirrors openAccountByCurrency's
// wait-for-real-content pattern rather than just waiting for the heading.
export async function openMessages(page: Page): Promise<void> {
  await page.locator('.btn-icon[title="Messages"]').click();
  await expect(page.getByRole("heading", { name: "Messages" })).toBeVisible();
  await expect(page.locator(".message-row, .empty-state").first()).toBeVisible();
}

// 0 when the badge isn't rendered at all (app.component.ts only shows it once unreadCount() > 0),
// not an error - most read-state assertions want that treated as a normal, valid count.
export async function unreadBadgeCount(page: Page): Promise<number> {
  const badge = page.locator(".unread-badge");
  if ((await badge.count()) === 0) {
    return 0;
  }
  return Number(await badge.innerText());
}
