import { Component, OnInit, ChangeDetectionStrategy, DestroyRef, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { Router } from "@angular/router";
import { FormsModule } from "@angular/forms";
import { Store } from "@ngrx/store";
import { Actions, ofType } from "@ngrx/effects";
import { toSignal, takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Account, Currency } from "../../models/bank.models";
import { AuthService } from "../../services/auth.service";
import { AccountsActions } from "../../store/accounts/accounts.actions";
import {
  selectAccounts,
  selectAccountsLoading,
  selectAddingAccount,
  selectAccountsError,
  selectCurrencyTotals,
} from "../../store/accounts/accounts.selectors";

@Component({
  selector: "app-accounts",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <!-- Header row -->
      <div class="page-header">
        <div>
          <h1>Accounts</h1>
        </div>
        <button class="btn btn-primary" (click)="openAddAccount()">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path
              d="M7 1v12M1 7h12"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
          </svg>
          New Account
        </button>
      </div>

      <!-- Error -->
      @if (error()) {
        <div class="error-banner">{{ error() }}</div>
      }

      <!-- Loading -->
      @if (loading()) {
        <div class="loading-state">
          <div class="spinner"></div>
          <span class="text-muted text-sm">Loading accounts…</span>
        </div>
      }

      <!-- Total balance summary -->
      @if (!loading() && accounts().length > 0) {
        <div class="balance-summary">
          @for (item of currencyTotals(); track item.currency) {
            <div class="summary-pill">
              <span class="currency-badge currency-{{ item.currency.toLowerCase() }}">{{ item.currency }}</span>
              <span class="summary-amount">{{ item.total | number: "1.2-2" }}</span>
            </div>
          }
        </div>
      }

      <!-- Account grid -->
      @if (!loading()) {
        <div class="accounts-grid">
          @for (account of accounts(); track account.id) {
            <div class="account-card" (click)="goToAccount(account)">
              <div class="account-card-header">
                <span class="currency-badge currency-{{ account.currency.toLowerCase() }}">
                  {{ account.currency }}
                </span>
                <svg class="chevron-right" width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <path
                    d="M6 4l4 4-4 4"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </div>
              <div class="account-balance">
                <span class="balance-amount">{{ formatAmount(account.balance) }}</span>
                <span class="balance-currency">{{ account.currency }}</span>
              </div>
              <div class="account-number monospace text-muted text-sm">
                Account {{ account.accountNumber }}
              </div>
            </div>
          }
          <!-- Empty state -->
          @if (accounts().length === 0) {
            <div class="empty-state">
              <div class="empty-state-icon">🏦</div>
              <p>No accounts yet</p>
              <button
                class="btn btn-primary btn-sm"
                style="margin-top:8px"
                (click)="openAddAccount()"
              >
                Open first account
              </button>
            </div>
          }
        </div>
      }

      <!-- Add Account Modal -->
      @if (showAddAccount) {
        <div class="modal-overlay" (click)="onOverlayClick($event)">
          <div class="modal" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h3>Open New Account</h3>
              <button class="modal-close" (click)="showAddAccount = false">×</button>
            </div>
            <div class="modal-body">
              <div class="form-group">
                <label class="form-label">Currency</label>
                <select class="form-select" [(ngModel)]="newCurrency">
                  <option value="EUR">Euro (EUR)</option>
                  <option value="USD">US Dollar (USD)</option>
                  <option value="CHF">Swiss Franc (CHF)</option>
                  <option value="GBP">British Pound (GBP)</option>
                  <option value="SEK">Swedish Krona (SEK)</option>
                  <option value="VND">Vietnamese Dong (VND)</option>
                </select>
              </div>
              <p class="text-muted text-sm">
                The new account will be opened with a zero balance in the selected currency.
              </p>
              @if (error()) {
                <div class="error-banner">{{ error() }}</div>
              }
            </div>
            <div class="modal-footer">
              <button class="btn btn-ghost" (click)="showAddAccount = false">Cancel</button>
              <button
                class="btn btn-primary"
                (click)="addAccount()"
                [disabled]="addingAccount()"
              >
                {{ addingAccount() ? "Opening..." : error() ? "Retry" : "Open Account" }}
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .page-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        margin-bottom: 24px;
      }
      .balance-summary {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        margin-bottom: 24px;
        padding: 14px 20px;
        background: var(--surface-elevated);
        border: 1px solid var(--border);
        border-radius: var(--radius-md);
      }
      .summary-pill {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .summary-amount {
        font-family: "Space Grotesk", sans-serif;
        font-weight: 600;
        font-size: 15px;
      }
      .summary-pill:not(:last-child)::after {
        content: "·";
        color: var(--border);
        margin-left: 8px;
      }

      .accounts-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
        gap: 16px;
      }
      .account-card {
        background: var(--surface-elevated);
        border: 1px solid var(--border);
        border-radius: var(--radius-md);
        padding: 20px;
        cursor: pointer;
        transition: all 0.15s ease;
        box-shadow: var(--shadow-sm);
      }
      .account-card:hover {
        border-color: var(--accent);
        box-shadow: var(--shadow-md);
        transform: translateY(-2px);
      }
      .account-card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 16px;
      }
      .chevron-right {
        color: var(--ink-subtle);
      }
      .account-card:hover .chevron-right {
        color: var(--accent);
      }

      .account-balance {
        display: flex;
        align-items: baseline;
        gap: 6px;
        margin-bottom: 10px;
      }
      .balance-amount {
        font-family: "Space Grotesk", sans-serif;
        font-size: 26px;
        font-weight: 700;
        letter-spacing: -0.03em;
        color: var(--ink);
      }
      .balance-currency {
        font-size: 14px;
        color: var(--ink-muted);
        font-weight: 500;
      }
      .account-number {
        margin-top: 2px;
      }
    `,
  ],
})
export class AccountsComponent implements OnInit {
  private store = inject(Store);
  private authService = inject(AuthService);
  private router = inject(Router);
  private actions$ = inject(Actions);
  private destroyRef = inject(DestroyRef);

  accounts = toSignal(this.store.select(selectAccounts), { initialValue: [] as Account[] });
  loading = toSignal(this.store.select(selectAccountsLoading), { initialValue: false });
  addingAccount = toSignal(this.store.select(selectAddingAccount), { initialValue: false });
  error = toSignal(this.store.select(selectAccountsError), { initialValue: null });
  currencyTotals = toSignal(this.store.select(selectCurrencyTotals), { initialValue: [] });

  showAddAccount = false;
  newCurrency: Currency = "EUR";

  // Fixed for the lifetime of one modal session so a retry after a failure reuses the same
  // key and is safely deduplicated by the backend.
  private createAccountIdempotencyKey = crypto.randomUUID();

  ngOnInit() {
    const user = this.authService.getUser();
    if (!user) {
      this.router.navigate(["/login"]);
      return;
    }
    this.store.dispatch(AccountsActions.loadAccounts({ userId: user.userId }));

    // Only a *Success* closes the modal. On failure it stays open (showing the error inline)
    // so a manual retry reuses the same idempotency key instead of starting a fresh attempt.
    this.actions$
      .pipe(
        ofType(AccountsActions.createAccountSuccess),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.showAddAccount = false;
        this.newCurrency = "EUR";
      });
  }

  goToAccount(account: Account) {
    this.router.navigate(["/accounts", account.id]);
  }

  openAddAccount() {
    this.showAddAccount = true;
    this.createAccountIdempotencyKey = crypto.randomUUID();
  }

  addAccount() {
    this.store.dispatch(
      AccountsActions.createAccount({
        currency: this.newCurrency,
        idempotencyKey: this.createAccountIdempotencyKey,
      }),
    );
  }

  onOverlayClick(_event: MouseEvent) {
    this.showAddAccount = false;
  }

  formatAmount(amount: number): string {
    return new Intl.NumberFormat("en-US", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  }
}
