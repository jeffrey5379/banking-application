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
    service.getAccountsByUser(1).subscribe();
    httpMock.expectOne('/api/accounts/user/1').flush([]);
  });

  it('getAccountSummary → GET /api/accounts/:id', () => {
    service.getAccountSummary(5).subscribe();
    httpMock.expectOne('/api/accounts/5').flush({});
  });

  it('createAccount → POST /api/accounts with currency', () => {
    service.createAccount({ currency: 'USD' }).subscribe();
    const req = httpMock.expectOne('/api/accounts');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ currency: 'USD' });
    req.flush({});
  });

  // ── Money operations ─────────────────────────────────────────────────────────

  it('credit → POST /api/accounts/:id/credit with amount and description', () => {
    service.credit(3, { amount: 100, description: 'Deposit' }).subscribe();
    const req = httpMock.expectOne('/api/accounts/3/credit');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 100, description: 'Deposit' });
    req.flush({});
  });

  it('debit → POST /api/accounts/:id/debit', () => {
    service.debit(3, { amount: 50 }).subscribe();
    const req = httpMock.expectOne('/api/accounts/3/debit');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 50 });
    req.flush({});
  });

  it('exchange → POST /api/accounts/:id/exchange with targetAccountId', () => {
    service.exchange(4, { amount: 200, targetAccountId: 7 }).subscribe();
    const req = httpMock.expectOne('/api/accounts/4/exchange');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 200, targetAccountId: 7 });
    req.flush([]);
  });

  // ── Transactions ─────────────────────────────────────────────────────────────

  it('getTransactionsPaged → GET with page and size query params', () => {
    service.getTransactionsPaged(2, 1, 15).subscribe();
    httpMock
      .expectOne('/api/accounts/2/transactions?page=1&size=15')
      .flush({ content: [], page: 1, totalElements: 0, last: true });
  });

  it('getTransaction → GET /api/accounts/transactions/:id', () => {
    service.getTransaction(42).subscribe();
    httpMock.expectOne('/api/accounts/transactions/42').flush({});
  });

  it('getBalanceHistory → GET /api/accounts/:id/balance-history', () => {
    service.getBalanceHistory(1).subscribe();
    httpMock.expectOne('/api/accounts/1/balance-history').flush([]);
  });

  // ── Exchange rates ────────────────────────────────────────────────────────────

  it('getExchangeRates → GET /api/exchange-rates', () => {
    service.getExchangeRates().subscribe();
    httpMock.expectOne('/api/exchange-rates').flush({});
  });
});
