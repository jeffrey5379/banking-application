import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Store } from '@ngrx/store';
import { AccountsActions } from '../store/accounts/accounts.actions';
import { ToastService } from './toast.service';
import { MessagesService } from './messages.service';
import { MessagePriority } from '../models/bank.models';
import { environment } from '../../environments/environment';

interface AccountEvent {
  accountId: string;
  accountNumber: string;
  type: string;
  amount: number;
  balanceAfter: number;
  currency: string;
  description: string;
}

interface MessageCreatedEvent {
  id: string;
  subject: string;
  body: string;
  receivedAt: string;
  priority: MessagePriority;
}

const RECONNECT_DELAY_MS = 3000;

// Proactively renew the stream before anything else gets a chance to kill it - e.g. Node's
// default 5-minute request timeout on the Angular dev-server proxy, or a load balancer's own
// idle/max-duration limit in prod. Well under any of those, so the swap always happens on our
// terms rather than being detected reactively via onerror after the fact.
//
// Sourced from environment.ts (swapped for environment.e2e.ts under the "e2e" build
// configuration - see angular.json), rather than hardcoded here, so 04-balance-updates.spec.ts's
// SSE-renewal test doesn't have to wait the real 4 minutes to observe one.
const MAX_CONNECTION_AGE_MS = environment.notificationRenewMs;

// Wraps the browser's EventSource for notification-service's live balance-update stream.
// EventSource can't send an Authorization header, so each (re)connect first exchanges the
// caller's JWT for a short-lived one-time ticket (see notification-service's TicketService),
// then opens the stream with that ticket in the query string instead of the token itself.
//
// Reconnection is handled explicitly here rather than relying on EventSource's built-in retry:
// a retry would resend the exact same URL, and a ticket is single-use/15s-lived, so it would
// just fail forever. Every reconnect attempt fetches a fresh ticket instead.
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private eventSource: EventSource | null = null;
  private stopped = true;
  private renewTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private http: HttpClient,
    private store: Store,
    private toastService: ToastService,
    private messagesService: MessagesService,
  ) {}

  connect(): void {
    if (!this.stopped) {
      return;
    }
    this.stopped = false;
    this.openStream();
  }

  disconnect(): void {
    this.stopped = true;
    this.clearRenewTimer();
    this.eventSource?.close();
    this.eventSource = null;
  }

  private openStream(): void {
    if (this.stopped) {
      return;
    }

    this.http.post<{ ticket: string }>('/api/notifications/ticket', {}).subscribe({
      next: ({ ticket }) => this.startEventSource(ticket),
      error: () => this.scheduleReconnect(),
    });
  }

  private startEventSource(ticket: string): void {
    if (this.stopped) {
      return;
    }

    const eventSource = new EventSource(`/api/notifications/stream?ticket=${ticket}`);
    this.eventSource = eventSource;
    this.renewTimer = setTimeout(() => this.renew(eventSource), MAX_CONNECTION_AGE_MS);

    eventSource.addEventListener('balance-update', (event: MessageEvent<string>) => {
      const data: AccountEvent = JSON.parse(event.data);
      this.store.dispatch(
        AccountsActions.accountBalanceUpdated({ accountId: data.accountId, balance: data.balanceAfter }),
      );
      if (data.type === 'TRANSFER_IN') {
        this.toastService.show(
          `Account ${data.accountNumber} balance changed.`,
          `+${data.amount} ${data.currency}`,
          data.description,
        );
      }
    });

    eventSource.addEventListener('message-created', (event: MessageEvent<string>) => {
      const data: MessageCreatedEvent = JSON.parse(event.data);
      this.messagesService.addMessage({
        id: data.id,
        subject: data.subject,
        body: data.body,
        receivedAt: data.receivedAt,
        read: false,
        priority: data.priority,
      });
      this.toastService.show('You have a new message:', data.subject, '');
    });

    eventSource.onerror = () => {
      eventSource.close();
      if (this.eventSource === eventSource) {
        this.clearRenewTimer();
        this.eventSource = null;
      }
      this.scheduleReconnect();
    };
  }

  // Fires from the timer scheduled in startEventSource - a no-op if that connection has already
  // been superseded in the meantime (e.g. onerror beat the timer to it).
  private renew(current: EventSource): void {
    if (this.stopped || this.eventSource !== current) {
      return;
    }
    current.close();
    this.eventSource = null;
    this.openStream();
  }

  private clearRenewTimer(): void {
    if (this.renewTimer) {
      clearTimeout(this.renewTimer);
      this.renewTimer = null;
    }
  }

  private scheduleReconnect(): void {
    if (this.stopped) {
      return;
    }
    setTimeout(() => this.openStream(), RECONNECT_DELAY_MS);
  }
}
