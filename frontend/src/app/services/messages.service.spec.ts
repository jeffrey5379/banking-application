import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MessagesService } from './messages.service';
import { Message } from '../models/bank.models';

const MESSAGE: Message = {
  id: '1',
  subject: 'Suspicious login attempt detected',
  body: 'We detected a login attempt from a new device.',
  receivedAt: '2026-07-27T14:32:00Z',
  read: false,
  priority: 'HIGH',
};

describe('MessagesService', () => {
  let service: MessagesService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MessagesService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MessagesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loadIfNeeded → GET /api/notifications/messages, populates messages() and unreadCount()', () => {
    service.loadIfNeeded();

    const req = httpMock.expectOne('/api/notifications/messages');
    expect(req.request.method).toBe('GET');
    req.flush([MESSAGE, { ...MESSAGE, id: '2', read: true }]);

    expect(service.messages().length).toBe(2);
    expect(service.unreadCount()).toBe(1);
    expect(service.loading()).toBe(false);
    expect(service.error()).toBeNull();
  });

  it('loadIfNeeded → does not re-fetch once already loaded', () => {
    service.loadIfNeeded();
    httpMock.expectOne('/api/notifications/messages').flush([MESSAGE]);

    service.loadIfNeeded();
    httpMock.expectNone('/api/notifications/messages');
  });

  it('loadIfNeeded → does not fire a second request while the first is still in flight', () => {
    service.loadIfNeeded();
    service.loadIfNeeded();
    httpMock.expectOne('/api/notifications/messages');
  });

  it('loadIfNeeded → sets error() on failure and allows a retry', () => {
    service.loadIfNeeded();
    httpMock
      .expectOne('/api/notifications/messages')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(service.error()).toBe('Failed to load messages.');
    expect(service.loading()).toBe(false);

    service.loadIfNeeded();
    httpMock.expectOne('/api/notifications/messages').flush([MESSAGE]);
    expect(service.error()).toBeNull();
  });

  it('markAsRead → optimistically marks the message read, then POSTs /read', () => {
    service.loadIfNeeded();
    httpMock.expectOne('/api/notifications/messages').flush([MESSAGE]);

    service.markAsRead('1');
    expect(service.messages()[0].read).toBe(true);
    expect(service.unreadCount()).toBe(0);

    const req = httpMock.expectOne('/api/notifications/messages/1/read');
    expect(req.request.method).toBe('POST');
    req.flush({ ...MESSAGE, read: true });
  });

  it('markAsRead → rolls back to unread if the request fails', () => {
    service.loadIfNeeded();
    httpMock.expectOne('/api/notifications/messages').flush([MESSAGE]);

    service.markAsRead('1');
    expect(service.messages()[0].read).toBe(true);

    httpMock
      .expectOne('/api/notifications/messages/1/read')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(service.messages()[0].read).toBe(false);
    expect(service.unreadCount()).toBe(1);
  });

  it('markAsRead → no-op (no request) for an already-read message', () => {
    service.loadIfNeeded();
    httpMock.expectOne('/api/notifications/messages').flush([{ ...MESSAGE, read: true }]);

    service.markAsRead('1');
    httpMock.expectNone('/api/notifications/messages/1/read');
  });

  it('addMessage → prepends a message pushed over SSE once the list is already loaded', () => {
    service.loadIfNeeded();
    httpMock.expectOne('/api/notifications/messages').flush([MESSAGE]);

    const pushed: Message = { ...MESSAGE, id: '2', subject: 'New one', read: false };
    service.addMessage(pushed);

    expect(service.messages()).toEqual([pushed, MESSAGE]);
    expect(service.unreadCount()).toBe(2);
  });

  it('addMessage → ignored before the initial load has completed (upcoming fetch already includes it)', () => {
    const pushed: Message = { ...MESSAGE, id: '2' };
    service.addMessage(pushed);

    expect(service.messages()).toEqual([]);

    service.loadIfNeeded();
    httpMock.expectOne('/api/notifications/messages').flush([MESSAGE]);
    expect(service.messages()).toEqual([MESSAGE]);
  });

  it('clear → resets state and allows loadIfNeeded() to fetch again', () => {
    service.loadIfNeeded();
    httpMock.expectOne('/api/notifications/messages').flush([MESSAGE]);

    service.clear();
    expect(service.messages()).toEqual([]);

    service.loadIfNeeded();
    httpMock.expectOne('/api/notifications/messages').flush([MESSAGE]);
  });
});
