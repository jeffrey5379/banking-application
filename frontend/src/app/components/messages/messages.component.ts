import { Component, ChangeDetectionStrategy, OnInit, inject, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { Message } from "../../models/bank.models";
import { MessagesService } from "../../services/messages.service";

@Component({
  selector: "app-messages",
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="page-container">
      <a routerLink="/" class="btn btn-ghost btn-sm back-link">
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
          <path
            d="M10 4l-4 4 4 4"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        Back
      </a>

      <div class="page-header">
        <h1>Messages</h1>
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
          <span class="text-muted text-sm">Loading messages…</span>
        </div>
      }

      @if (!loading() && !error()) {
        <div class="card" style="overflow:hidden">
          @if (messages().length === 0) {
            <div class="empty-state">
              <p>No messages yet</p>
            </div>
          }
          @if (messages().length > 0) {
            <div class="message-list">
              @for (message of messages(); track message.id) {
                <div
                  class="message-row"
                  [class.unread]="!message.read"
                  (click)="openMessage(message)"
                >
                  <span class="unread-dot" [class.visible]="!message.read"></span>
                  <div class="message-row-main">
                    <div class="message-row-top">
                      <span class="message-subject">{{ message.subject }}</span>
                      @if (message.priority === "HIGH") {
                        <span class="badge badge-debit">Important</span>
                      }
                    </div>
                  </div>
                  <span class="message-date text-muted text-sm">{{
                    formatDate(message.receivedAt)
                  }}</span>
                </div>
              }
            </div>
          }
        </div>
      }

      <!-- Message detail modal -->
      @if (selectedMessage(); as message) {
        <div class="modal-overlay">
          <div class="modal" style="max-width:520px">
            <div class="modal-header">
              <h3>{{ message.subject }}</h3>
              <button class="modal-close" (click)="closeMessage()">×</button>
            </div>
            <div class="modal-body">
              <div class="message-detail-meta">
                <span class="text-muted text-sm">{{ formatDate(message.receivedAt) }}</span>
                @if (message.priority === "HIGH") {
                  <span class="badge badge-debit">Important</span>
                }
              </div>
              <p class="message-detail-body">{{ message.body }}</p>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .back-link {
        margin-bottom: 16px;
      }
      .page-header {
        margin-bottom: 24px;
      }
      .message-list {
        display: flex;
        flex-direction: column;
      }
      .message-row {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 16px 24px;
        border-bottom: 1px solid var(--border-subtle);
        cursor: pointer;
        transition: background 0.1s;
      }
      .message-row:last-child {
        border-bottom: none;
      }
      .message-row:hover {
        background: var(--accent-light);
      }
      .unread-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        flex-shrink: 0;
        visibility: hidden;
        background: var(--accent);
      }
      .unread-dot.visible {
        visibility: visible;
      }
      .message-row-main {
        flex: 1;
        min-width: 0;
      }
      .message-row-top {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .message-subject {
        font-size: 14px;
        color: var(--ink);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .message-row.unread .message-subject {
        font-weight: 600;
      }
      .message-date {
        flex-shrink: 0;
        white-space: nowrap;
      }
      .message-detail-meta {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 12px;
      }
      .message-detail-body {
        white-space: pre-wrap;
        line-height: 1.6;
      }
    `,
  ],
})
export class MessagesComponent implements OnInit {
  private messagesService = inject(MessagesService);

  messages = this.messagesService.messages;
  loading = this.messagesService.loading;
  error = this.messagesService.error;
  selectedMessage = signal<Message | null>(null);

  ngOnInit(): void {
    this.messagesService.loadIfNeeded();
  }

  retry(): void {
    this.messagesService.loadIfNeeded();
  }

  openMessage(message: Message): void {
    this.selectedMessage.set(message);
    if (!message.read) {
      this.messagesService.markAsRead(message.id);
    }
  }

  closeMessage(): void {
    this.selectedMessage.set(null);
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
