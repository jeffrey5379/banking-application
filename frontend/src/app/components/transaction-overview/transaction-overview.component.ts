import {
  Component,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
  inject,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { Store } from "@ngrx/store";
import { toSignal } from "@angular/core/rxjs-interop";
import { TransactionActions } from "../../store/transaction/transaction.actions";
import {
  selectTransaction,
  selectTransactionLoading,
  selectTransactionError,
} from "../../store/transaction/transaction.selectors";

@Component({
  selector: "app-transaction-overview",
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="page-container">
      <!-- Breadcrumb -->
      <div class="breadcrumb-row">
        <nav class="breadcrumb">
          <a routerLink="/accounts">Accounts</a>
          <span class="sep">›</span>
          <a [routerLink]="['/accounts', tx()?.accountId]">{{
            tx()?.accountNumber || "…"
          }}</a>
          <span class="sep">›</span>
          <span>Transaction #{{ tx()?.id }}</span>
        </nav>
        @if (tx()) {
          <a
            [routerLink]="['/accounts', tx()!.accountId]"
            class="btn btn-ghost btn-sm"
          >
            ← Back to Account
          </a>
        }
      </div>

      @if (loading()) {
        <div class="loading-state">
          <div class="spinner"></div>
          <span class="text-muted text-sm">Loading transaction…</span>
        </div>
      }

      @if (error()) {
        <div class="error-banner">{{ error() }}</div>
      }

      @if (!loading() && tx()) {
        <!-- Hero -->
        <div class="tx-hero card" [class]="'tx-hero--' + txKind(tx()!.type)">
          <button
            class="export-btn btn btn-ghost btn-sm"
            (click)="exportPdf()"
            title="Export to PDF"
          >
            <svg
              width="14"
              height="14"
              viewBox="0 0 14 14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M7 1v8M4 6l3 3 3-3" />
              <path d="M1 10v1a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2v-1" />
            </svg>
            Export to PDF
          </button>
          <div class="tx-hero-icon">
            @if (tx()!.type === "CREDIT") {
              <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
                <circle cx="18" cy="18" r="18" fill="#dcfce7" />
                <path
                  d="M11 18h14M19 12l6 6-6 6"
                  stroke="#16a34a"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            }
            @if (tx()!.type === "DEBIT") {
              <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
                <circle cx="18" cy="18" r="18" fill="#fee2e2" />
                <path
                  d="M25 18H11M17 12l-6 6 6 6"
                  stroke="#dc2626"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            }
            @if (tx()!.type === "EXCHANGE_IN") {
              <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
                <circle cx="18" cy="18" r="18" fill="#dcfce7" />
                <path
                  d="M11 18h14M19 12l6 6-6 6"
                  stroke="#16a34a"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            }
            @if (tx()!.type === "EXCHANGE_OUT") {
              <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
                <circle cx="18" cy="18" r="18" fill="#fee2e2" />
                <path
                  d="M25 18H11M17 12l-6 6 6 6"
                  stroke="#dc2626"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            }
          </div>
          <div class="tx-hero-body">
            <span [class]="'badge ' + badgeClass(tx()!.type)">{{
              txLabel(tx()!.type)
            }}</span>
            <div class="tx-amount">
              <span class="tx-sign">{{ txSign(tx()!.type) }}</span>
              <span class="tx-value">{{ formatAmount(tx()!.amount) }}</span>
              <span class="tx-curr">{{ tx()!.currency }}</span>
            </div>
            <p class="tx-desc">{{ tx()!.description }}</p>
          </div>
        </div>

        <!-- Detail grid -->
        <div class="detail-grid card">
          <div class="detail-row">
            <span class="detail-label">Transaction ID</span>
            <span class="detail-value monospace">#{{ tx()!.id }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">Account</span>
            <span class="detail-value">
              <a [routerLink]="['/accounts', tx()!.accountId]" class="link">{{
                tx()!.accountNumber
              }}</a>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">Type</span>
            <span class="detail-value">
              <span [class]="'badge ' + badgeClass(tx()!.type)">{{
                txLabel(tx()!.type)
              }}</span>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">Amount</span>
            <span class="detail-value" style="font-weight:600">
              {{ txSign(tx()!.type) }}{{ formatAmount(tx()!.amount) }}
              {{ tx()!.currency }}
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">Balance</span>
            <span class="detail-value monospace"
              >{{ formatAmount(tx()!.balanceAfter) }} {{ tx()!.currency }}</span
            >
          </div>
          <div class="detail-row">
            <span class="detail-label">Date & Time</span>
            <span class="detail-value">{{ formatDate(tx()!.createdAt) }}</span>
          </div>
          @if (tx()!.exchangeRate) {
            <div class="detail-row">
              <span class="detail-label">Exchange Rate</span>
              <span class="detail-value monospace">{{
                tx()!.exchangeRate | number: "1.6-6"
              }}</span>
            </div>
          }
          @if (tx()!.relatedAccountNumber) {
            <div class="detail-row">
              <span class="detail-label">Related Account</span>
              <span class="detail-value">
                <a
                  [routerLink]="['/accounts', tx()!.relatedAccountId]"
                  class="link"
                  >Account #{{ tx()!.relatedAccountNumber }}</a
                >
              </span>
            </div>
          }
          <div class="detail-row">
            <span class="detail-label">Description</span>
            <span class="detail-value">{{ tx()!.description }}</span>
          </div>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .breadcrumb-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 20px;
      }
      .breadcrumb {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 13px;
        color: var(--ink-muted);
      }
      .btn-sm {
        padding: 4px 12px;
        font-size: 12px;
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
      .link {
        color: var(--accent);
        text-decoration: none;
      }
      .link:hover {
        text-decoration: underline;
      }

      .tx-hero {
        position: relative;
        display: flex;
        align-items: center;
        gap: 24px;
        padding: 28px;
        margin-bottom: 16px;
      }
      .tx-hero--credit {
        border-left: 4px solid var(--success);
      }
      .tx-hero--debit {
        border-left: 4px solid var(--danger);
      }
      .tx-hero--exchange {
        border-left: 4px solid var(--warning);
      }

      .export-btn {
        position: absolute;
        top: 16px;
        right: 16px;
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 12px;
      }

      .tx-hero-icon {
        font-size: 36px;
        line-height: 1;
      }
      .tx-hero-body {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 8px;
      }
      .tx-amount {
        display: flex;
        align-items: baseline;
        gap: 6px;
      }
      .tx-sign {
        font-size: 28px;
        font-weight: 300;
        color: var(--ink-muted);
        line-height: 1;
      }
      .tx-value {
        font-family: "Space Grotesk", sans-serif;
        font-size: 36px;
        font-weight: 700;
        letter-spacing: -0.03em;
      }
      .tx-curr {
        font-size: 16px;
        font-weight: 500;
        color: var(--ink-muted);
      }
      .tx-desc {
        color: var(--ink-muted);
        font-size: 14px;
      }

      .detail-grid {
        overflow: hidden;
      }
      .detail-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 13px 20px;
        border-bottom: 1px solid var(--border-subtle);
      }
      .detail-row:last-child {
        border-bottom: none;
      }
      .detail-label {
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: var(--ink-muted);
      }
      .detail-value {
        font-size: 14px;
        text-align: right;
      }
    `,
  ],
})
export class TransactionOverviewComponent implements OnInit, OnDestroy {
  private store = inject(Store);
  private route = inject(ActivatedRoute);

  tx = toSignal(this.store.select(selectTransaction), { initialValue: null });
  loading = toSignal(this.store.select(selectTransactionLoading), {
    initialValue: false,
  });
  error = toSignal(this.store.select(selectTransactionError), {
    initialValue: null,
  });

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get("id")!;
    this.store.dispatch(TransactionActions.loadTransaction({ id }));
  }

  ngOnDestroy() {
    this.store.dispatch(TransactionActions.clear());
  }

  async exportPdf() {
    const tx = this.tx();
    if (!tx) return;
    const { default: jsPDF } = await import("jspdf");
    const doc = new jsPDF({ unit: "pt", format: "a4" });
    const W = doc.internal.pageSize.getWidth();

    doc.setFillColor(14, 116, 144);
    doc.rect(0, 0, W, 88, "F");

    doc.setTextColor(255, 255, 255);
    doc.setFont("helvetica", "bold");
    doc.setFontSize(22);
    doc.text("OCTOPUS BANK", W / 2, 38, { align: "center" });

    doc.setFont("helvetica", "normal");
    doc.setFontSize(11);
    doc.setTextColor(186, 230, 253);
    doc.text("TRANSACTION DETAILS", W / 2, 62, { align: "center" });

    const isPositive = tx.type === "CREDIT" || tx.type === "EXCHANGE_IN";
    doc.setTextColor(isPositive ? "#16a34a" : "#dc2626");
    doc.setFont("helvetica", "bold");
    doc.setFontSize(28);
    doc.text(
      `${this.txSign(tx.type)} ${this.formatAmount(tx.amount)} ${tx.currency}`,
      W / 2,
      148,
      { align: "center" },
    );

    doc.setDrawColor("#e5e7eb");
    doc.setLineWidth(0.5);
    doc.line(40, 188, W - 40, 188);

    // Built-in jsPDF fonts use Latin-1 (cp1252); replace characters outside that range
    // so splitTextToSize can measure widths correctly (unknown chars get width=0 otherwise)
    const latin1 = (s: string) =>
      s.replace(/→/g, "->").replace(/[^\x00-\xFF]/g, "?");

    const rows: [string, string][] = [
      ["Transaction ID", `#${tx.id}`],
      ["Account Number", tx.accountNumber],
      ["Date & Time", this.formatDate(tx.createdAt)],
      ["Description", latin1(tx.description)],
      ["Balance", `${this.formatAmount(tx.balanceAfter)} ${tx.currency}`],
    ];
    if (tx.exchangeRate)
      rows.push(["Exchange Rate", tx.exchangeRate.toFixed(6)]);
    if (tx.relatedAccountNumber)
      rows.push(["Related Account", `#${tx.relatedAccountNumber}`]);

    // Max width available for the value column (right ~60% of content area)
    const valueMaxWidth = (W - 80) * 0.58;
    const lineH = 14;

    let y = 212;
    rows.forEach(([label, value], i) => {
      doc.setFont("helvetica", "normal");
      doc.setFontSize(11);
      const valueLines: string[] = doc.splitTextToSize(value, valueMaxWidth);
      const rowH = Math.max(32, valueLines.length * lineH + 18);

      if (i % 2 === 0) {
        doc.setFillColor("#f9fafb");
        doc.rect(40, y - 18, W - 80, rowH, "F");
      }

      doc.setFont("helvetica", "bold");
      doc.setFontSize(9);
      doc.setTextColor("#9ca3af");
      doc.text(label.toUpperCase(), 56, y);

      doc.setFont("helvetica", "normal");
      doc.setFontSize(11);
      doc.setTextColor("#111827");
      valueLines.forEach((line, li) => {
        doc.text(line, W - 56, y + li * lineH, { align: "right" });
      });

      y += rowH;
    });

    doc.setDrawColor("#e5e7eb");
    doc.line(40, y + 12, W - 40, y + 12);
    doc.setFontSize(8);
    doc.setTextColor("#9ca3af");
    doc.text(
      `Generated on ${new Date().toLocaleString("en-GB")}`,
      W / 2,
      y + 32,
      { align: "center" },
    );

    doc.save(`transaction-${tx.id}.pdf`);
  }

  txKind(type: string): string {
    if (type === "CREDIT" || type === "EXCHANGE_IN") return "credit";
    if (type === "EXCHANGE_OUT") return "exchange";
    return "debit";
  }

  badgeClass(type: string): string {
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

  txSign(type: string): string {
    return type === "CREDIT" || type === "EXCHANGE_IN" ? "+" : "-";
  }

  formatAmount(amount: number): string {
    return new Intl.NumberFormat("en-US", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleString("en-GB", {
      weekday: "long",
      day: "2-digit",
      month: "long",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  }
}
