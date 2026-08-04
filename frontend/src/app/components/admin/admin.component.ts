import { Component, ChangeDetectionStrategy, OnInit, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule, NgForm } from "@angular/forms";
import { AdminService } from "../../services/admin.service";
import { AdminUserSummary, KycStatus, MessagePriority } from "../../models/bank.models";

@Component({
  selector: "app-admin",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <h1>Administration</h1>
      </div>

      @if (error()) {
        <div class="error-banner">
          {{ error() }}
          <button class="btn btn-ghost btn-sm" style="margin-left:8px" (click)="retry()">
            Retry
          </button>
        </div>
      }

      @if (loading()) {
        <div class="loading-state">
          <div class="spinner"></div>
          <span class="text-muted text-sm">Loading users…</span>
        </div>
      }

      @if (!loading() && !error()) {
        <div class="card" style="overflow:hidden">
          @if (users().length === 0) {
            <div class="empty-state">
              <p>No users yet</p>
            </div>
          }
          @if (users().length > 0) {
            <table class="data-table">
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Active</th>
                  <th>KYC status</th>
                  <th style="text-align:right">Send message</th>
                </tr>
              </thead>
              <tbody>
                @for (user of users(); track user.id) {
                  <tr>
                    <td>{{ user.username }}</td>
                    <td>{{ user.email }}</td>
                    <td>
                      <button
                        class="switch"
                        [class.switch-on]="user.active"
                        type="button"
                        role="switch"
                        [attr.aria-checked]="user.active"
                        [attr.aria-label]="
                          (user.active ? 'Deactivate ' : 'Activate ') + user.username
                        "
                        (click)="requestToggle(user)"
                      >
                        <span class="switch-thumb"></span>
                      </button>
                    </td>
                    <td>
                      <span class="badge" [class]="statusBadgeClass(user.kycStatus)">{{
                        statusLabel(user.kycStatus)
                      }}</span>
                    </td>
                    <td class="actions-cell">
                      <button
                        class="btn-icon"
                        type="button"
                        title="Send message"
                        [attr.aria-label]="'Send message to ' + user.username"
                        (click)="openSendMessage(user)"
                      >
                        <svg width="18" height="18" viewBox="0 0 20 20" fill="none">
                          <rect
                            x="2"
                            y="4"
                            width="16"
                            height="12"
                            rx="2"
                            stroke="currentColor"
                            stroke-width="1.5"
                          />
                          <path
                            d="M3 5.5l7 5 7-5"
                            stroke="currentColor"
                            stroke-width="1.5"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                          />
                        </svg>
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>
      }

      <!-- Toggle-active confirmation modal -->
      @if (pendingToggle(); as target) {
        <div class="modal-overlay">
          <div class="modal" style="max-width:420px">
            <div class="modal-header">
              <h3>{{ target.active ? "Deactivate" : "Activate" }} user</h3>
              <button class="modal-close" (click)="cancelToggle()">×</button>
            </div>
            <div class="modal-body">
              <p>
                Are you sure you want to
                {{ target.active ? "deactivate" : "activate" }}
                <strong>{{ target.username }}</strong>?
                @if (target.active) {
                  <span class="text-muted text-sm"> They will no longer be able to sign in.</span>
                }
              </p>
            </div>
            <div class="modal-footer">
              <button class="btn btn-ghost" (click)="cancelToggle()">Cancel</button>
              <button class="btn btn-primary" (click)="confirmToggle()">Confirm</button>
            </div>
          </div>
        </div>
      }

      <!-- Send message modal -->
      @if (messageTarget(); as target) {
        <div class="modal-overlay">
          <div class="modal">
            <div class="modal-header">
              <h3>Send message to {{ target.username }}</h3>
              <button class="modal-close" (click)="closeSendMessage()">×</button>
            </div>
            <div class="modal-body">
              <form (ngSubmit)="sendMessage(messageForm, target)" #messageForm="ngForm" novalidate>
                <div class="form-group">
                  <label class="form-label">Subject</label>
                  <input
                    class="form-input"
                    [(ngModel)]="messageSubject"
                    name="subject"
                    #subjectField="ngModel"
                    placeholder="Message subject"
                    required
                    [class.input-invalid]="
                      subjectField.invalid && (subjectField.touched || messageForm.submitted)
                    "
                  />
                </div>
                <div class="form-group">
                  <label class="form-label">Body</label>
                  <textarea
                    class="form-input"
                    rows="4"
                    [(ngModel)]="messageBody"
                    name="body"
                    #bodyField="ngModel"
                    placeholder="Message body"
                    required
                    [class.input-invalid]="
                      bodyField.invalid && (bodyField.touched || messageForm.submitted)
                    "
                  ></textarea>
                </div>
                <div class="form-group" style="margin-bottom:0">
                  <label class="form-label">Priority</label>
                  <select class="form-select" [(ngModel)]="messagePriority" name="priority">
                    <option value="NORMAL">Normal</option>
                    <option value="HIGH">Important</option>
                  </select>
                </div>
                @if (messageError()) {
                  <div class="error-banner" style="margin-top:16px">{{ messageError() }}</div>
                }
                <div class="modal-footer" style="padding:20px 0 0">
                  <button class="btn btn-ghost" type="button" (click)="closeSendMessage()">
                    Cancel
                  </button>
                  <button class="btn btn-primary" type="submit" [disabled]="sendingMessage()">
                    {{ sendingMessage() ? "Sending…" : "Send" }}
                  </button>
                </div>
              </form>
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
        margin-bottom: 24px;
      }
      .actions-cell {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
      }
      .btn-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 30px;
        height: 30px;
        border-radius: var(--radius-sm);
        border: 1px solid var(--border);
        background: transparent;
        color: var(--accent);
        cursor: pointer;
        transition: all 0.15s;
      }
      .btn-icon:hover {
        background: var(--accent-light);
        border-color: var(--accent);
      }
      .badge-success {
        background: var(--success-light);
        color: var(--success);
      }
      .badge-warning {
        background: var(--warning-light);
        color: var(--warning);
      }
      .badge-danger {
        background: var(--danger-light);
        color: var(--danger);
      }
      .badge-muted {
        background: var(--surface-inset);
        color: var(--ink-muted);
      }
      .input-invalid {
        border-color: var(--danger) !important;
      }

      .switch {
        position: relative;
        width: 38px;
        height: 22px;
        padding: 0;
        border: none;
        border-radius: 11px;
        background: var(--border);
        cursor: pointer;
        transition: background 0.15s;
      }
      .switch-on {
        background: var(--success);
      }
      .switch-thumb {
        position: absolute;
        top: 2px;
        left: 2px;
        width: 18px;
        height: 18px;
        border-radius: 50%;
        background: white;
        box-shadow: var(--shadow-sm);
        transition: transform 0.15s;
      }
      .switch-on .switch-thumb {
        transform: translateX(16px);
      }
    `,
  ],
})
export class AdminComponent implements OnInit {
  private adminService = inject(AdminService);

  users = this.adminService.users;
  loading = this.adminService.loading;
  error = this.adminService.error;

  pendingToggle = signal<AdminUserSummary | null>(null);
  messageTarget = signal<AdminUserSummary | null>(null);
  messageSubject = "";
  messageBody = "";
  messagePriority: MessagePriority = "NORMAL";
  sendingMessage = signal(false);
  messageError = signal("");

  ngOnInit(): void {
    this.adminService.loadIfNeeded();
  }

  retry(): void {
    this.adminService.loadIfNeeded();
  }

  requestToggle(user: AdminUserSummary) {
    this.pendingToggle.set(user);
  }

  cancelToggle() {
    this.pendingToggle.set(null);
  }

  confirmToggle() {
    const target = this.pendingToggle();
    if (!target) return;
    this.adminService.setActive(target.id, !target.active);
    this.pendingToggle.set(null);
  }

  openSendMessage(user: AdminUserSummary) {
    this.messageTarget.set(user);
    this.messageSubject = "";
    this.messageBody = "";
    this.messagePriority = "NORMAL";
    this.messageError.set("");
  }

  closeSendMessage() {
    this.messageTarget.set(null);
  }

  sendMessage(form: NgForm, target: AdminUserSummary) {
    if (form.invalid) return;
    this.sendingMessage.set(true);
    this.messageError.set("");
    this.adminService
      .sendMessage(target.id, this.messageSubject, this.messageBody, this.messagePriority)
      .subscribe({
        next: () => {
          this.sendingMessage.set(false);
          this.messageTarget.set(null);
        },
        error: () => {
          this.sendingMessage.set(false);
          this.messageError.set("Failed to send message. Please try again.");
        },
      });
  }

  statusLabel(status: KycStatus): string {
    switch (status) {
      case "VERIFIED":
        return "Verified";
      case "PENDING":
        return "Pending";
      case "REJECTED":
        return "Rejected";
      default:
        return "Not started";
    }
  }

  statusBadgeClass(status: KycStatus): string {
    switch (status) {
      case "VERIFIED":
        return "badge-success";
      case "PENDING":
        return "badge-warning";
      case "REJECTED":
        return "badge-danger";
      default:
        return "badge-muted";
    }
  }
}
