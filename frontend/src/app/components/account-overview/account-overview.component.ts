import {
  Component,
  OnInit,
  OnDestroy,
  ViewChild,
  ElementRef,
  ChangeDetectionStrategy,
  inject,
  effect,
  DestroyRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { FormsModule } from "@angular/forms";
import { BaseChartDirective } from "ng2-charts";
import { ChartData, ChartOptions } from "chart.js";
import { Store } from "@ngrx/store";
import { Actions, ofType } from "@ngrx/effects";
import { toSignal, takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { BalancePoint } from "../../models/bank.models";
import { AccountDetailActions } from "../../store/account-detail/account-detail.actions";
import {
  selectAccount,
  selectTransactions,
  selectBalanceHistory,
  selectStats,
  selectTotalElements,
  selectHasMore,
  selectAccountDetailLoading,
  selectLoadingMore,
  selectOperationLoading,
  selectAccountDetailError,
  selectOtherAccounts,
  selectExchangeRates,
} from "../../store/account-detail/account-detail.selectors";

type ModalType = "credit" | "debit" | "exchange" | null;

@Component({
  selector: "app-account-overview",
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, BaseChartDirective],
  template: `
    <div class="page-container">
      <!-- Breadcrumb -->
      <nav class="breadcrumb">
        <a routerLink="/accounts">Accounts</a>
        <span class="sep">›</span>
        <span>{{ account()?.accountNumber || "…" }}</span>
      </nav>

      <!-- Loading — only when no cached data to show yet -->
      @if (loading() && !account()) {
        <div class="loading-state">
          <div class="spinner"></div>
          <span class="text-muted text-sm">Loading account…</span>
        </div>
      }

      @if (account()) {
        <div>
          <!-- Account hero card -->
          <div class="account-hero card">
            <div class="hero-left">
              <div class="hero-currency">
                <span
                  class="currency-badge currency-{{
                    account()!.currency.toLowerCase()
                  }}"
                  >{{ account()!.currency }}</span
                >
                <span class="hero-label text-muted text-sm"
                  >Current balance</span
                >
              </div>
              <div class="hero-balance">
                <span class="hero-amount">{{
                  formatAmount(account()!.balance)
                }}</span>
                <span class="hero-curr">{{ account()!.currency }}</span>
              </div>
              <div class="monospace text-muted text-sm" style="margin-top:8px">
                Account {{ account()!.accountNumber }}
              </div>
            </div>
            <div class="hero-actions">
              <button class="btn btn-primary" (click)="openModal('credit')">
                <svg width="13" height="13" viewBox="0 0 13 13">
                  <path
                    d="M6.5 1v11M1 6.5h11"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                  />
                </svg>
                Add Money
              </button>
              <button class="btn btn-ghost" (click)="openModal('debit')">
                <svg width="13" height="13" viewBox="0 0 13 13">
                  <path
                    d="M1 6.5h11"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                  />
                </svg>
                Debit
              </button>
              <button class="btn btn-ghost" (click)="openModal('exchange')">
                <svg width="14" height="14" viewBox="0 0 14 14">
                  <path
                    d="M1 4h9m0 0-3-3m3 3-3 3M13 10H4m0 0 3-3M4 10l3 3"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                Exchange
              </button>
            </div>
          </div>

          <!-- Error -->
          @if (error()) {
            <div class="error-banner" style="margin-top:16px">
              {{ error() }}
            </div>
          }

          <!-- Balance chart -->
          @if (balanceHistory().length >= 2) {
            <div class="card chart-card">
              <div class="chart-header">
                <span class="chart-title">Balance History</span>
                <div class="chart-stats">
                  <div class="chart-stat chart-stat-in">
                    <span class="chart-stat-label">Total In</span>
                    <span class="chart-stat-value"
                      >+{{ formatAmount(stats()?.totalIn ?? 0) }}
                      {{ account()?.currency }}</span
                    >
                  </div>
                  <div class="chart-stat chart-stat-out">
                    <span class="chart-stat-label">Total Out</span>
                    <span class="chart-stat-value"
                      >−{{ formatAmount(stats()?.totalOut ?? 0) }}
                      {{ account()?.currency }}</span
                    >
                  </div>
                </div>
              </div>
              <div class="chart-container">
                <canvas
                  baseChart
                  [data]="lineChartData"
                  [options]="lineChartOptions"
                  type="line"
                ></canvas>
              </div>
            </div>
          }

          <!-- Transaction history -->
          <div class="section-header">
            <h2>Transactions</h2>
            @if (totalElements() > 0) {
              <span class="tx-count">{{ totalElements() }} transactions</span>
            }
          </div>
          <div class="card" style="overflow:hidden">
            @if (transactions().length === 0 && !loadingMore()) {
              <div class="empty-state">
                <p>No transactions yet</p>
                <p class="text-sm text-muted">Add money to get started.</p>
              </div>
            }
            @if (transactions().length > 0) {
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Type</th>
                    <th>Description</th>
                    <th>Date</th>
                    <th style="text-align:right">Amount</th>
                    <th style="text-align:right">Balance</th>
                  </tr>
                </thead>
                <tbody>
                  @for (tx of transactions(); track tx.id) {
                    <tr (click)="goToTransaction(tx.id)">
                      <td>
                        <span [class]="'badge ' + txBadgeClass(tx.type)">{{
                          txLabel(tx.type)
                        }}</span>
                      </td>
                      <td class="tx-desc">{{ tx.description }}</td>
                      <td class="text-muted text-sm">
                        {{ formatDate(tx.createdAt) }}
                      </td>
                      <td style="text-align:right">
                        <span
                          [class]="txAmountClass(tx.type)"
                          style="font-weight:600; font-family:'Space Grotesk',sans-serif"
                        >
                          {{ txSign(tx.type) }}{{ formatAmount(tx.amount) }}
                          <small class="text-muted" style="font-weight:400">
                            {{ tx.currency }}</small
                          >
                        </span>
                      </td>
                      <td
                        style="text-align:right"
                        class="monospace text-sm text-muted"
                      >
                        {{ formatAmount(tx.balanceAfter) }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
            <!-- Infinite scroll sentinel -->
            <div #sentinel></div>
            <!-- Loading more spinner -->
            @if (loadingMore()) {
              <div class="loading-more">
                <div class="spinner spinner-sm"></div>
                <span class="text-muted text-sm">Loading more…</span>
              </div>
            }
          </div>
        </div>
      }

      <!-- Modal -->
      @if (activeModal) {
        <div class="modal-overlay">
          <div class="modal">
            <!-- Credit -->
            @if (activeModal === "credit") {
              <div class="modal-header">
                <h3>Add Money</h3>
                <button class="modal-close" (click)="closeModal()">×</button>
              </div>
              <div class="modal-body">
                <div class="form-group">
                  <label class="form-label"
                    >Amount ({{ account()?.currency }})</label
                  >
                  <input
                    class="form-input"
                    type="number"
                    min="0.01"
                    step="0.01"
                    [(ngModel)]="modalAmount"
                    placeholder="0.00"
                  />
                </div>
                <div class="form-group">
                  <label class="form-label">Description (optional)</label>
                  <input
                    class="form-input"
                    type="text"
                    [(ngModel)]="modalDesc"
                    placeholder="e.g. Salary"
                  />
                </div>
                @if (error()) {
                  <div class="error-banner">{{ error() }}</div>
                }
              </div>
              <div class="modal-footer">
                <button class="btn btn-ghost" (click)="closeModal()">
                  Cancel
                </button>
                <button
                  class="btn btn-primary"
                  (click)="submitCredit()"
                  [disabled]="operationLoading()"
                >
                  {{ operationLoading() ? "Processing..." : error() ? "Retry" : "Add Money" }}
                </button>
              </div>
            }
            <!-- Debit -->
            @if (activeModal === "debit") {
              <div class="modal-header">
                <h3>Debit Funds</h3>
                <button class="modal-close" (click)="closeModal()">×</button>
              </div>
              <div class="modal-body">
                <div class="form-group">
                  <label class="form-label"
                    >Amount ({{ account()?.currency }})</label
                  >
                  <input
                    class="form-input"
                    type="number"
                    min="0.01"
                    step="0.01"
                    [(ngModel)]="modalAmount"
                    placeholder="0.00"
                  />
                </div>
                <div class="form-group">
                  <label class="form-label">Description (optional)</label>
                  <input
                    class="form-input"
                    type="text"
                    [(ngModel)]="modalDesc"
                    placeholder="e.g. Rent payment"
                  />
                </div>
                <p class="text-muted text-sm">
                  Available: {{ formatAmount(account()?.balance || 0) }}
                  {{ account()?.currency }}
                </p>
                @if (error()) {
                  <div class="error-banner">{{ error() }}</div>
                }
              </div>
              <div class="modal-footer">
                <button class="btn btn-ghost" (click)="closeModal()">
                  Cancel
                </button>
                <button
                  class="btn btn-primary"
                  (click)="submitDebit()"
                  [disabled]="operationLoading()"
                >
                  {{ operationLoading() ? "Processing..." : error() ? "Retry" : "Debit" }}
                </button>
              </div>
            }
            <!-- Exchange -->
            @if (activeModal === "exchange") {
              <div class="modal-header">
                <h3>Currency Exchange</h3>
                <button class="modal-close" (click)="closeModal()">×</button>
              </div>
              <div class="modal-body">
                <div class="form-group">
                  <label class="form-label"
                    >Amount to Exchange ({{ account()?.currency }})</label
                  >
                  <input
                    class="form-input"
                    type="number"
                    min="0.01"
                    step="0.01"
                    [(ngModel)]="modalAmount"
                    placeholder="0.00"
                  />
                </div>
                <div class="form-group">
                  <label class="form-label">Target Account</label>
                  <select class="form-select" [(ngModel)]="targetAccountId">
                    <option [ngValue]="null" disabled>Select account…</option>
                    @for (a of otherAccounts(); track a.id) {
                      <option [ngValue]="a.id">
                        {{ a.accountNumber }} — {{ formatAmount(a.balance) }}
                        {{ a.currency }}
                      </option>
                    }
                  </select>
                </div>
                @if (targetAccountId) {
                  <div class="rate-preview">
                    <span>
                      Rate: 1 {{ account()?.currency }} =
                      {{ getExchangeRate() | number: "1.4-4" }}
                      {{ getTargetCurrency() }}
                      @if (modalAmount) {
                        <span>
                          You will receive
                          {{ formatAmount(getConvertedAmount()) }}
                          {{ getTargetCurrency() }}
                        </span>
                      }
                    </span>
                  </div>
                }
                @if (error()) {
                  <div class="error-banner">{{ error() }}</div>
                }
              </div>
              <div class="modal-footer">
                <button class="btn btn-ghost" (click)="closeModal()">
                  Cancel
                </button>
                <button
                  class="btn btn-primary"
                  (click)="submitExchange()"
                  [disabled]="operationLoading() || !targetAccountId"
                >
                  {{ operationLoading() ? "Processing..." : error() ? "Retry" : "Exchange" }}
                </button>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .breadcrumb {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 20px;
        font-size: 13px;
        color: var(--ink-muted);
      }
      .breadcrumb a {
        color: var(--accent);
        text-decoration: none;
      }
      .breadcrumb a:hover {
        text-decoration: underline;
      }
      .sep {
        color: var(--border);
      }

      .account-hero {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        padding: 28px 28px;
        gap: 24px;
        flex-wrap: wrap;
        margin-bottom: 24px;
      }
      .hero-left {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }
      .hero-currency {
        display: flex;
        align-items: center;
        gap: 10px;
      }
      .hero-balance {
        display: flex;
        align-items: baseline;
        gap: 8px;
        margin-top: 4px;
      }
      .hero-amount {
        font-family: "Space Grotesk", sans-serif;
        font-size: 38px;
        font-weight: 700;
        letter-spacing: -0.03em;
      }
      .hero-curr {
        font-size: 16px;
        font-weight: 500;
        color: var(--ink-muted);
      }
      .hero-actions {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
        align-items: flex-start;
        padding-top: 4px;
      }

      .section-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin: 28px 0 12px;
      }
      .tx-count {
        font-size: 12px;
        color: var(--ink-muted);
        background: var(--surface-inset);
        padding: 2px 8px;
        border-radius: 20px;
      }

      .tx-desc {
        max-width: 240px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .rate-preview {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 12px;
        color: var(--ink-muted);
        background: var(--surface);
        padding: 8px 12px;
        border-radius: var(--radius-sm);
      }

      .chart-card {
        padding: 20px 20px 16px;
        margin-bottom: 0;
      }
      .chart-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 16px;
      }
      .chart-title {
        font-size: 13px;
        font-weight: 600;
      }
      .chart-stats {
        display: flex;
        gap: 20px;
      }
      .chart-stat {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 1px;
      }
      .chart-stat-label {
        font-size: 10px;
        font-weight: 500;
        text-transform: uppercase;
        letter-spacing: 0.06em;
        color: var(--ink-muted);
      }
      .chart-stat-value {
        font-family: "Space Grotesk", sans-serif;
        font-size: 13px;
        font-weight: 700;
      }
      .chart-stat-in .chart-stat-value {
        color: #059669;
      }
      .chart-stat-out .chart-stat-value {
        color: #dc2626;
      }
      .chart-container {
        position: relative;
        height: 220px;
      }

      .loading-more {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        padding: 16px;
      }

      .spinner-sm {
        width: 16px;
        height: 16px;
        border-width: 2px;
      }
    `,
  ],
})
export class AccountOverviewComponent implements OnInit, OnDestroy {
  private store = inject(Store);
  private actions$ = inject(Actions);
  private destroyRef = inject(DestroyRef);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  account = toSignal(this.store.select(selectAccount), { initialValue: null });
  transactions = toSignal(this.store.select(selectTransactions), {
    initialValue: [],
  });
  balanceHistory = toSignal(this.store.select(selectBalanceHistory), {
    initialValue: [] as BalancePoint[],
  });
  totalElements = toSignal(this.store.select(selectTotalElements), {
    initialValue: 0,
  });
  hasMore = toSignal(this.store.select(selectHasMore), { initialValue: false });
  loading = toSignal(this.store.select(selectAccountDetailLoading), {
    initialValue: false,
  });
  loadingMore = toSignal(this.store.select(selectLoadingMore), {
    initialValue: false,
  });
  operationLoading = toSignal(this.store.select(selectOperationLoading), {
    initialValue: false,
  });
  error = toSignal(this.store.select(selectAccountDetailError), {
    initialValue: null,
  });
  otherAccounts = toSignal(this.store.select(selectOtherAccounts), {
    initialValue: [],
  });
  exchangeRates = toSignal(this.store.select(selectExchangeRates), {
    initialValue: {} as Record<string, number>,
  });

  stats = toSignal(this.store.select(selectStats), { initialValue: null });

  activeModal: ModalType = null;
  modalAmount: number | null = null;
  modalDesc = "";
  targetAccountId: string | null = null;

  // Fixed for the lifetime of one modal session so a retry after a failure (same modal,
  // same inputs) reuses the same key and is safely deduplicated by the backend.
  private operationIdempotencyKey = crypto.randomUUID();

  lineChartData: ChartData<"line"> = { labels: [], datasets: [] };

  lineChartOptions: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: "#ffffff",
        borderColor: "rgba(0,0,0,0.08)",
        borderWidth: 1,
        titleColor: "#6b7280",
        bodyColor: "#111827",
        bodyFont: {
          family: "'Space Grotesk', sans-serif",
          weight: "bold",
          size: 14,
        },
        titleFont: { size: 11 },
        padding: 10,
        displayColors: false,
        callbacks: {
          label: (ctx) =>
            `${this.formatAmount(Number(ctx.raw))} ${this.account()?.currency ?? ""}`,
          title: (items) =>
            this.formatChartDate(
              this.balanceHistory()[items[0].dataIndex]?.createdAt ?? "",
            ),
        },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { maxTicksLimit: 7, color: "#9ca3af", font: { size: 11 } },
      },
      y: {
        grid: { color: "rgba(0,0,0,0.05)" },
        ticks: {
          color: "#9ca3af",
          font: { size: 11 },
          callback: (value) => this.formatAmount(Number(value)),
        },
      },
    },
  };

  private observer: IntersectionObserver | null = null;

  constructor() {
    effect(() => {
      const history = this.balanceHistory();
      if (history.length >= 2) {
        this.buildChart(history);
      }
    });
  }

  @ViewChild("sentinel") set sentinelEl(ref: ElementRef | undefined) {
    if (ref) {
      this.observer?.disconnect();
      this.observer = new IntersectionObserver(
        ([entry]) => {
          if (entry.isIntersecting && this.hasMore() && !this.loadingMore()) {
            this.store.dispatch(AccountDetailActions.loadMoreTransactions());
          }
        },
        { rootMargin: "200px" },
      );
      this.observer.observe(ref.nativeElement);
    }
  }

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get("id")!;
    this.store.dispatch(AccountDetailActions.loadAccount({ id }));

    // Only a *Success* closes the modal. On failure it stays open (showing the error inline)
    // so a manual retry reuses the same idempotency key instead of starting a fresh attempt.
    this.actions$
      .pipe(
        ofType(
          AccountDetailActions.submitCreditSuccess,
          AccountDetailActions.submitDebitSuccess,
          AccountDetailActions.submitExchangeSuccess,
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.closeModal());
  }

  ngOnDestroy() {
    this.observer?.disconnect();
    this.store.dispatch(AccountDetailActions.clear());
  }

  openModal(type: ModalType) {
    this.activeModal = type;
    this.modalAmount = null;
    this.modalDesc = "";
    this.targetAccountId = null;
    this.operationIdempotencyKey = crypto.randomUUID();
  }

  closeModal() {
    this.activeModal = null;
  }

  submitCredit() {
    const account = this.account();
    if (!account || !this.modalAmount) return;
    this.store.dispatch(
      AccountDetailActions.submitCredit({
        accountId: account.id,
        req: { amount: this.modalAmount, description: this.modalDesc },
        idempotencyKey: this.operationIdempotencyKey,
      }),
    );
  }

  submitDebit() {
    const account = this.account();
    if (!account || !this.modalAmount) return;
    this.store.dispatch(
      AccountDetailActions.submitDebit({
        accountId: account.id,
        req: { amount: this.modalAmount, description: this.modalDesc },
        idempotencyKey: this.operationIdempotencyKey,
      }),
    );
  }

  submitExchange() {
    const account = this.account();
    if (!account || !this.modalAmount || !this.targetAccountId) return;
    this.store.dispatch(
      AccountDetailActions.submitExchange({
        accountId: account.id,
        req: {
          amount: this.modalAmount,
          targetAccountId: this.targetAccountId,
        },
        idempotencyKey: this.operationIdempotencyKey,
      }),
    );
  }

  getTargetCurrency(): string {
    const target = this.otherAccounts().find(
      (a) => a.id === this.targetAccountId,
    );
    return target?.currency || "";
  }

  getExchangeRate(): number {
    const account = this.account();
    if (!account || !this.targetAccountId) return 0;
    const tc = this.getTargetCurrency();
    return this.exchangeRates()[`${account.currency}_${tc}`] || 0;
  }

  getConvertedAmount(): number {
    if (!this.modalAmount) return 0;
    return this.modalAmount * this.getExchangeRate();
  }

  goToTransaction(id: string) {
    this.router.navigate(["/transactions", id]);
  }

  private buildChart(history: BalancePoint[]) {
    this.lineChartData = {
      labels: history.map((p) => this.formatChartDate(p.createdAt)),
      datasets: [
        {
          data: history.map((p) => p.balance),
          borderColor: "#0e7490",
          backgroundColor: "rgba(14, 116, 144, 0.08)",
          fill: true,
          tension: 0.3,
          pointRadius: 3,
          pointHoverRadius: 5,
          pointBackgroundColor: "#ffffff",
          pointBorderColor: "#0e7490",
          pointBorderWidth: 2,
          pointHoverBackgroundColor: "#0e7490",
        },
      ],
    };
  }

  txBadgeClass(type: string): string {
    if (type === "CREDIT" || type === "EXCHANGE_IN") return "badge-credit";
    if (type === "DEBIT" || type === "EXCHANGE_OUT") return "badge-debit";
    return "badge-exchange";
  }

  txLabel(type: string): string {
    const map: Record<string, string> = {
      CREDIT: "Credit",
      DEBIT: "Debit",
      EXCHANGE_IN: "Exchange In",
      EXCHANGE_OUT: "Exchange Out",
    };
    return map[type] || type;
  }

  txAmountClass(type: string): string {
    return type === "CREDIT" || type === "EXCHANGE_IN"
      ? "amount-positive"
      : "amount-negative";
  }

  txSign(type: string): string {
    return type === "CREDIT" || type === "EXCHANGE_IN" ? "+" : "-";
  }

  formatAmount(amount: number): string {
    return new Intl.NumberFormat("en-US", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  }

  formatChartDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString("en-GB", {
      day: "2-digit",
      month: "short",
    });
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString("en-GB", {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }
}
