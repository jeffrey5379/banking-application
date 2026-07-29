import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { NotificationService } from './notification.service';
import { ToastService } from './toast.service';
import { MessagesService } from './messages.service';
import { AccountsActions } from '../store/accounts/accounts.actions';

const RECONNECT_DELAY_MS = 3000;
const MAX_CONNECTION_AGE_MS = 4 * 60 * 1000;

// jsdom has no EventSource - this stands in for the browser's, controlled manually from tests
// (emit a named event, or trigger a connection failure) instead of going over a real network.
class MockEventSource {
  static instances: MockEventSource[] = [];

  onerror: ((ev: Event) => void) | null = null;
  closed = false;
  private listeners = new Map<string, Array<(ev: MessageEvent) => void>>();

  constructor(public url: string) {
    MockEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (ev: MessageEvent) => void): void {
    const list = this.listeners.get(type) ?? [];
    list.push(listener);
    this.listeners.set(type, list);
  }

  close(): void {
    this.closed = true;
  }

  emit(type: string, data: unknown): void {
    const event = { data: JSON.stringify(data) } as MessageEvent;
    (this.listeners.get(type) ?? []).forEach((listener) => listener(event));
  }

  triggerError(): void {
    this.closed = true;
    this.onerror?.(new Event('error'));
  }

  static latest(): MockEventSource {
    return MockEventSource.instances[MockEventSource.instances.length - 1];
  }
}

function accountEvent(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    ownerId: 'owner-1',
    accountId: 'acc-1',
    accountNumber: '1234567890',
    type: 'TRANSFER_IN',
    amount: 50,
    balanceAfter: 150,
    currency: 'EUR',
    description: 'Transfer from alice (0987654321)',
    occurredAt: '2026-07-28T10:00:00',
    ...overrides,
  };
}

function messageCreatedEvent(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    ownerId: 'owner-1',
    id: 'msg-1',
    subject: 'Suspicious login attempt detected',
    body: 'We detected a login attempt from a new device.',
    receivedAt: '2026-07-28T10:00:00Z',
    priority: 'HIGH',
    ...overrides,
  };
}

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;
  let store: MockStore;
  let toastService: jest.Mocked<Pick<ToastService, 'show'>>;
  let messagesService: jest.Mocked<Pick<MessagesService, 'addMessage'>>;
  let originalEventSource: typeof EventSource;

  beforeEach(() => {
    originalEventSource = (globalThis as any).EventSource;
    (globalThis as any).EventSource = MockEventSource;
    MockEventSource.instances = [];

    toastService = { show: jest.fn() };
    messagesService = { addMessage: jest.fn() };

    TestBed.configureTestingModule({
      providers: [
        NotificationService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMockStore(),
        { provide: ToastService, useValue: toastService },
        { provide: MessagesService, useValue: messagesService },
      ],
    });

    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    jest.useFakeTimers();
  });

  afterEach(() => {
    service.disconnect();
    httpMock.verify();
    jest.useRealTimers();
    (globalThis as any).EventSource = originalEventSource;
  });

  // Drives connect() through the ticket exchange and returns the EventSource it opened.
  function connectAndOpenStream(ticket = 'ticket-1'): MockEventSource {
    service.connect();
    httpMock.expectOne('/api/notifications/ticket').flush({ ticket });
    return MockEventSource.latest();
  }

  // ── Receiving events ──────────────────────────────────────────────────────

  it('exchanges a JWT for a ticket and opens the stream with it in the query string', () => {
    const es = connectAndOpenStream('my-ticket');
    expect(es.url).toBe('/api/notifications/stream?ticket=my-ticket');
  });

  it('dispatches accountBalanceUpdated with the account id and new balance from the event', () => {
    const es = connectAndOpenStream();

    es.emit('balance-update', accountEvent({ accountId: 'acc-1', balanceAfter: 150 }));

    expect(store.dispatch).toHaveBeenCalledWith(
      AccountsActions.accountBalanceUpdated({ accountId: 'acc-1', balance: 150 }),
    );
  });

  it('shows a toast for the recipient side of a transfer (TRANSFER_IN)', () => {
    const es = connectAndOpenStream();

    es.emit(
      'balance-update',
      accountEvent({
        type: 'TRANSFER_IN',
        accountNumber: '1234567890',
        amount: 50,
        currency: 'EUR',
        description: 'Transfer from alice (0987654321)',
      }),
    );

    expect(toastService.show).toHaveBeenCalledWith(
      'Account 1234567890 balance changed.',
      '+50 EUR',
      'Transfer from alice (0987654321)',
    );
  });

  it.each(['TRANSFER_OUT', 'EXCHANGE_IN', 'EXCHANGE_OUT'])(
    'does not show a toast for %s (only the recipient side of a transfer gets one)',
    (type) => {
      const es = connectAndOpenStream();

      es.emit('balance-update', accountEvent({ type }));

      expect(toastService.show).not.toHaveBeenCalled();
    },
  );

  it('adds the message and shows a toast when a message-created event arrives', () => {
    const es = connectAndOpenStream();

    es.emit('message-created', messageCreatedEvent({
      id: 'msg-1',
      subject: 'Suspicious login attempt detected',
      body: 'We detected a login attempt from a new device.',
      receivedAt: '2026-07-28T10:00:00Z',
      priority: 'HIGH',
    }));

    expect(messagesService.addMessage).toHaveBeenCalledWith({
      id: 'msg-1',
      subject: 'Suspicious login attempt detected',
      body: 'We detected a login attempt from a new device.',
      receivedAt: '2026-07-28T10:00:00Z',
      read: false,
      priority: 'HIGH',
    });
    expect(toastService.show).toHaveBeenCalledWith(
      'You have a new message:',
      'Suspicious login attempt detected',
      '',
    );
  });

  it('still patches the store balance for event types that do not show a toast', () => {
    const es = connectAndOpenStream();

    es.emit('balance-update', accountEvent({ type: 'EXCHANGE_OUT', accountId: 'acc-2', balanceAfter: 42 }));

    expect(store.dispatch).toHaveBeenCalledWith(
      AccountsActions.accountBalanceUpdated({ accountId: 'acc-2', balance: 42 }),
    );
  });

  it('does not open a second stream if connect() is called again while already connected', () => {
    connectAndOpenStream('ticket-1');

    service.connect();

    httpMock.expectNone('/api/notifications/ticket');
  });

  // ── Recovering from a dropped connection ─────────────────────────────────

  describe('reconnection after an error', () => {
    it('closes the broken connection and opens a fresh one with a new ticket after a delay', () => {
      const first = connectAndOpenStream('ticket-1');

      first.triggerError();
      expect(first.closed).toBe(true);
      httpMock.expectNone('/api/notifications/ticket');

      jest.advanceTimersByTime(RECONNECT_DELAY_MS);
      httpMock.expectOne('/api/notifications/ticket').flush({ ticket: 'ticket-2' });

      const second = MockEventSource.latest();
      expect(second).not.toBe(first);
      expect(second.url).toBe('/api/notifications/stream?ticket=ticket-2');
    });

    it('does not reconnect once disconnect() has been called', () => {
      const first = connectAndOpenStream('ticket-1');
      service.disconnect();

      first.triggerError();
      jest.advanceTimersByTime(RECONNECT_DELAY_MS);

      httpMock.expectNone('/api/notifications/ticket');
    });
  });

  // ── Proactive renewal, so nothing external gets to kill the stream first ────

  describe('proactive renewal', () => {
    it('closes and reopens the stream on its own once the max connection age elapses', () => {
      const first = connectAndOpenStream('ticket-1');

      jest.advanceTimersByTime(MAX_CONNECTION_AGE_MS);
      httpMock.expectOne('/api/notifications/ticket').flush({ ticket: 'ticket-2' });

      expect(first.closed).toBe(true);
      const second = MockEventSource.latest();
      expect(second).not.toBe(first);
      expect(second.url).toBe('/api/notifications/stream?ticket=ticket-2');
    });

    it('keeps receiving and dispatching events on the connection opened by a renewal', () => {
      connectAndOpenStream('ticket-1');

      jest.advanceTimersByTime(MAX_CONNECTION_AGE_MS);
      httpMock.expectOne('/api/notifications/ticket').flush({ ticket: 'ticket-2' });

      const renewed = MockEventSource.latest();
      renewed.emit(
        'balance-update',
        accountEvent({ type: 'TRANSFER_IN', accountId: 'acc-3', balanceAfter: 77 }),
      );

      expect(store.dispatch).toHaveBeenCalledWith(
        AccountsActions.accountBalanceUpdated({ accountId: 'acc-3', balance: 77 }),
      );
      expect(toastService.show).toHaveBeenCalled();
    });

    it('renews again after the connection opened by a previous renewal reaches its own max age', () => {
      connectAndOpenStream('ticket-1');

      jest.advanceTimersByTime(MAX_CONNECTION_AGE_MS);
      httpMock.expectOne('/api/notifications/ticket').flush({ ticket: 'ticket-2' });
      const second = MockEventSource.latest();

      jest.advanceTimersByTime(MAX_CONNECTION_AGE_MS);
      httpMock.expectOne('/api/notifications/ticket').flush({ ticket: 'ticket-3' });

      expect(second.closed).toBe(true);
      const third = MockEventSource.latest();
      expect(third).not.toBe(second);
      expect(third.url).toBe('/api/notifications/stream?ticket=ticket-3');
    });

    it('does not renew after disconnect() has been called', () => {
      connectAndOpenStream('ticket-1');
      service.disconnect();

      jest.advanceTimersByTime(MAX_CONNECTION_AGE_MS);

      httpMock.expectNone('/api/notifications/ticket');
    });

    it('ignores a stale renewal timer for a connection already replaced by an error-triggered reconnect', () => {
      connectAndOpenStream('ticket-1');

      MockEventSource.latest().triggerError();
      jest.advanceTimersByTime(RECONNECT_DELAY_MS);
      httpMock.expectOne('/api/notifications/ticket').flush({ ticket: 'ticket-2' });

      // The replaced (first) connection's own renewal timer would fire at this point - it must
      // be a no-op, since the second connection's own timer isn't due for another
      // RECONNECT_DELAY_MS yet.
      jest.advanceTimersByTime(MAX_CONNECTION_AGE_MS - RECONNECT_DELAY_MS);
      httpMock.expectNone('/api/notifications/ticket');
    });
  });

  // ── Recovering from a ticket-request failure ─────────────────────────────

  it('retries fetching a ticket after a delay if the ticket request itself fails', () => {
    service.connect();
    httpMock.expectOne('/api/notifications/ticket').flush(null, { status: 500, statusText: 'Error' });

    expect(MockEventSource.instances).toHaveLength(0);

    jest.advanceTimersByTime(RECONNECT_DELAY_MS);
    httpMock.expectOne('/api/notifications/ticket').flush({ ticket: 'ticket-1' });

    expect(MockEventSource.instances).toHaveLength(1);
  });
});
