import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BankService } from './bank.service';

describe('BankService', () => {
  let service: BankService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BankService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BankService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── Accounts ─────────────────────────────────────────────────────────────────

  it('getAccountsByUser → GET /api/accounts/user/:id', () => {
    service.getAccountsByUser('1').subscribe();
    httpMock.expectOne('/api/accounts/user/1').flush([]);
  });

  it('getAccountSummary → GET /api/accounts/:id', () => {
    service.getAccountSummary('5').subscribe();
    httpMock.expectOne('/api/accounts/5').flush({});
  });

  it('createAccount → POST /api/accounts with currency and Idempotency-Key header', () => {
    service.createAccount({ currency: 'USD' }, 'idem-key-1').subscribe();
    const req = httpMock.expectOne('/api/accounts');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ currency: 'USD' });
    expect(req.request.headers.get('Idempotency-Key')).toBe('idem-key-1');
    req.flush({});
  });

  // ── Money operations ─────────────────────────────────────────────────────────

  it('exchange → POST /api/accounts/:id/exchange with targetAccountId and Idempotency-Key header', () => {
    service.exchange('4', { amount: 200, targetAccountId: '7' }, 'idem-key-4').subscribe();
    const req = httpMock.expectOne('/api/accounts/4/exchange');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 200, targetAccountId: '7' });
    expect(req.request.headers.get('Idempotency-Key')).toBe('idem-key-4');
    req.flush([]);
  });

  it('transfer → POST /api/accounts/:id/transfer with recipient details and Idempotency-Key header', () => {
    service.transfer('4', { amount: 50, targetUsername: 'bob', targetAccountNumber: 'ACC-002' }, 'idem-key-5').subscribe();
    const req = httpMock.expectOne('/api/accounts/4/transfer');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 50, targetUsername: 'bob', targetAccountNumber: 'ACC-002' });
    expect(req.request.headers.get('Idempotency-Key')).toBe('idem-key-5');
    req.flush([]);
  });

  it('checkRecipient → GET /api/accounts/recipient with username and accountNumber query params', () => {
    service.checkRecipient('bob', 'ACC-002').subscribe();
    const req = httpMock.expectOne('/api/accounts/recipient?username=bob&accountNumber=ACC-002');
    expect(req.request.method).toBe('GET');
    req.flush({ valid: true });
  });

  it('checkRecipient → URL-encodes username and accountNumber', () => {
    service.checkRecipient('bob smith', 'ACC 002').subscribe();
    httpMock
      .expectOne('/api/accounts/recipient?username=bob%20smith&accountNumber=ACC%20002')
      .flush({ valid: false });
  });

  // ── Transactions ─────────────────────────────────────────────────────────────

  it('getTransactionsPaged → GET with page and size query params', () => {
    service.getTransactionsPaged('2', 1, 15).subscribe();
    httpMock
      .expectOne('/api/accounts/2/transactions?page=1&size=15')
      .flush({ content: [], page: 1, totalElements: 0, last: true });
  });

  it('getTransaction → GET /api/accounts/transactions/:id', () => {
    service.getTransaction('42').subscribe();
    httpMock.expectOne('/api/accounts/transactions/42').flush({});
  });

  it('getBalanceHistory → GET /api/accounts/:id/balance-history', () => {
    service.getBalanceHistory('1').subscribe();
    httpMock.expectOne('/api/accounts/1/balance-history').flush([]);
  });

  // ── Exchange rates ────────────────────────────────────────────────────────────

  it('getExchangeRates → GET /api/exchange-rates', () => {
    service.getExchangeRates().subscribe();
    httpMock.expectOne('/api/exchange-rates').flush({});
  });
});
