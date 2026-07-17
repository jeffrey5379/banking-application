import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideMockActions } from '@ngrx/effects/testing';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { Observable, of, throwError } from 'rxjs';
import { Action } from '@ngrx/store';
import { AccountDetailEffects } from './account-detail.effects';
import { AccountDetailActions } from './account-detail.actions';
import { selectExchangeRates, selectAccountId, selectCurrentPage } from './account-detail.selectors';
import { BankService } from '../../services/bank.service';
import { AuthService } from '../../services/auth.service';
import { Account, Transaction } from '../../models/bank.models';

const mockTx: Transaction = {
  id: '1',
  accountId: '1',
  accountNumber: 'ACC-001',
  type: 'CREDIT',
  amount: 100,
  currency: 'EUR',
  balanceAfter: 1100,
  description: 'Test',
  createdAt: '2024-01-01T00:00:00Z',
};

describe('AccountDetailEffects', () => {
  let actions$: Observable<Action>;
  let effects: AccountDetailEffects;
  let store: MockStore;
  let bankService: jest.Mocked<Pick<
    BankService,
    | 'getAccountSummary'
    | 'getAccountsByUser'
    | 'getExchangeRates'
    | 'getTransactionsPaged'
    | 'getBalanceHistory'
    | 'credit'
    | 'debit'
    | 'exchange'
  >>;

  beforeEach(() => {
    bankService = {
      getAccountSummary: jest.fn(),
      getAccountsByUser: jest.fn(),
      getExchangeRates: jest.fn(),
      getTransactionsPaged: jest.fn(),
      getBalanceHistory: jest.fn(),
      credit: jest.fn(),
      debit: jest.fn(),
      exchange: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        AccountDetailEffects,
        provideMockActions(() => actions$),
        provideMockStore(),
        { provide: BankService, useValue: bankService },
        { provide: AuthService, useValue: { getUser: jest.fn().mockReturnValue(null) } },
        { provide: Router, useValue: { navigate: jest.fn() } },
      ],
    });

    effects = TestBed.inject(AccountDetailEffects);
    store = TestBed.inject(MockStore);
  });

  afterEach(() => store.resetSelectors());

  // ── Exchange rate cache guard ────────────────────────────────────────────────

  describe('loadExchangeRates$ (cache guard)', () => {
    it('calls the API and dispatches success when store has no rates', (done) => {
      store.overrideSelector(selectExchangeRates, {});
      store.refreshState();
      bankService.getExchangeRates.mockReturnValue(of({ EUR_USD: 0.92 }));
      actions$ = of(AccountDetailActions.loadExchangeRates());

      effects.loadExchangeRates$.subscribe((action) => {
        expect(bankService.getExchangeRates).toHaveBeenCalledTimes(1);
        expect(action).toEqual(
          AccountDetailActions.loadExchangeRatesSuccess({ rates: { EUR_USD: 0.92 } }),
        );
        done();
      });
    });

    it('skips the API call entirely when rates are already cached', () => {
      store.overrideSelector(selectExchangeRates, { EUR_USD: 0.92, EUR_GBP: 1.17 });
      store.refreshState();
      actions$ = of(AccountDetailActions.loadExchangeRates());

      const emitted: Action[] = [];
      effects.loadExchangeRates$.subscribe((a) => emitted.push(a));

      expect(bankService.getExchangeRates).not.toHaveBeenCalled();
      expect(emitted).toHaveLength(0);
    });
  });

  // ── Operation effects ────────────────────────────────────────────────────────

  describe('submitCredit$', () => {
    it('dispatches submitCreditSuccess on API success', (done) => {
      bankService.credit.mockReturnValue(of(mockTx));
      actions$ = of(
        AccountDetailActions.submitCredit({ accountId: '1', req: { amount: 100 }, idempotencyKey: 'idem-key-1' }),
      );

      effects.submitCredit$.subscribe((action) => {
        expect(action).toEqual(AccountDetailActions.submitCreditSuccess({ transaction: mockTx }));
        done();
      });
    });

    it('sends the idempotency key through to the backend', (done) => {
      bankService.credit.mockReturnValue(of(mockTx));
      actions$ = of(
        AccountDetailActions.submitCredit({ accountId: '1', req: { amount: 100 }, idempotencyKey: 'idem-key-1' }),
      );

      effects.submitCredit$.subscribe(() => {
        expect(bankService.credit).toHaveBeenCalledWith('1', { amount: 100 }, 'idem-key-1');
        done();
      });
    });

    it('dispatches submitCreditFailure with backend error message', (done) => {
      bankService.credit.mockReturnValue(
        throwError(() => ({ error: { message: 'Insufficient funds' } })),
      );
      actions$ = of(
        AccountDetailActions.submitCredit({ accountId: '1', req: { amount: 9999 }, idempotencyKey: 'idem-key-1' }),
      );

      effects.submitCredit$.subscribe((action) => {
        expect(action).toEqual(
          AccountDetailActions.submitCreditFailure({ error: 'Insufficient funds' }),
        );
        done();
      });
    });
  });

  describe('submitDebit$', () => {
    it('dispatches submitDebitSuccess on API success', (done) => {
      const debitTx = { ...mockTx, type: 'DEBIT' as const };
      bankService.debit.mockReturnValue(of(debitTx));
      actions$ = of(
        AccountDetailActions.submitDebit({ accountId: '1', req: { amount: 50 }, idempotencyKey: 'idem-key-2' }),
      );

      effects.submitDebit$.subscribe((action) => {
        expect(action).toEqual(AccountDetailActions.submitDebitSuccess({ transaction: debitTx }));
        done();
      });
    });

    it('dispatches submitDebitFailure on error', (done) => {
      bankService.debit.mockReturnValue(
        throwError(() => ({ error: { message: 'Debit not allowed' } })),
      );
      actions$ = of(
        AccountDetailActions.submitDebit({ accountId: '1', req: { amount: 50 }, idempotencyKey: 'idem-key-2' }),
      );

      effects.submitDebit$.subscribe((action) => {
        expect(action).toEqual(
          AccountDetailActions.submitDebitFailure({ error: 'Debit not allowed' }),
        );
        done();
      });
    });
  });

  describe('submitExchange$', () => {
    it('dispatches submitExchangeSuccess on API success', (done) => {
      bankService.exchange.mockReturnValue(of([mockTx]));
      actions$ = of(
        AccountDetailActions.submitExchange({
          accountId: '1',
          req: { amount: 200, targetAccountId: '2' },
          idempotencyKey: 'idem-key-3',
        }),
      );

      effects.submitExchange$.subscribe((action) => {
        expect(action).toEqual(
          AccountDetailActions.submitExchangeSuccess({ transactions: [mockTx] }),
        );
        done();
      });
    });
  });

  describe('refreshAccount$', () => {
    it('dispatches loadAccountSuccess on API success', (done) => {
      const account: Account = {
        id: '1',
        accountNumber: 'ACC-001',
        currency: 'EUR',
        balance: 1000,
        userId: '1',
        username: 'alice',
      };
      bankService.getAccountSummary.mockReturnValue(of(account));
      actions$ = of(AccountDetailActions.refreshAccount({ accountId: '1' }));

      effects.refreshAccount$.subscribe((action) => {
        expect(action).toEqual(AccountDetailActions.loadAccountSuccess({ account }));
        done();
      });
    });
  });

  describe('resyncOnFailure$', () => {
    beforeEach(() => {
      store.overrideSelector(selectAccountId, '1');
      store.refreshState();
    });

    it('refreshes account, transactions, balance history, and summary after submitCreditFailure', (done) => {
      actions$ = of(AccountDetailActions.submitCreditFailure({ error: 'Insufficient funds' }));

      const emitted: Action[] = [];
      effects.resyncOnFailure$.subscribe({
        next: (a) => emitted.push(a),
        complete: () => {
          expect(emitted).toEqual([
            AccountDetailActions.refreshAccount({ accountId: '1' }),
            AccountDetailActions.loadTransactions({ accountId: '1', page: 0 }),
            AccountDetailActions.loadBalanceHistory({ accountId: '1' }),
            AccountDetailActions.loadSummary({ accountId: '1' }),
          ]);
          done();
        },
      });
    });

    it('resyncs after submitDebitFailure and submitExchangeFailure too', (done) => {
      actions$ = of(
        AccountDetailActions.submitDebitFailure({ error: 'Debit not allowed' }),
        AccountDetailActions.submitExchangeFailure({ error: 'Exchange failed' }),
      );

      const emitted: Action[] = [];
      effects.resyncOnFailure$.subscribe({
        next: (a) => emitted.push(a),
        complete: () => {
          expect(emitted).toHaveLength(8);
          done();
        },
      });
    });
  });

  // ── Side-effect triggers ─────────────────────────────────────────────────────

  describe('refreshBalanceHistory$', () => {
    beforeEach(() => {
      store.overrideSelector(selectAccountId, '1');
      store.refreshState();
    });

    it('dispatches loadBalanceHistory after credit success', (done) => {
      actions$ = of(AccountDetailActions.submitCreditSuccess({ transaction: mockTx }));
      effects.refreshBalanceHistory$.subscribe((action) => {
        expect(action).toEqual(AccountDetailActions.loadBalanceHistory({ accountId: '1' }));
        done();
      });
    });

    it('dispatches loadBalanceHistory after debit success', (done) => {
      actions$ = of(AccountDetailActions.submitDebitSuccess({ transaction: mockTx }));
      effects.refreshBalanceHistory$.subscribe((action) => {
        expect(action).toEqual(AccountDetailActions.loadBalanceHistory({ accountId: '1' }));
        done();
      });
    });

    it('dispatches loadBalanceHistory after exchange success', (done) => {
      actions$ = of(AccountDetailActions.submitExchangeSuccess({ transactions: [mockTx] }));
      effects.refreshBalanceHistory$.subscribe((action) => {
        expect(action).toEqual(AccountDetailActions.loadBalanceHistory({ accountId: '1' }));
        done();
      });
    });
  });

  // ── Pagination ───────────────────────────────────────────────────────────────

  describe('loadMoreTransactions$', () => {
    it('dispatches loadTransactions with page incremented by 1', (done) => {
      store.overrideSelector(selectAccountId, '5');
      store.overrideSelector(selectCurrentPage, 2);
      store.refreshState();
      actions$ = of(AccountDetailActions.loadMoreTransactions());

      effects.loadMoreTransactions$.subscribe((action) => {
        expect(action).toEqual(
          AccountDetailActions.loadTransactions({ accountId: '5', page: 3 }),
        );
        done();
      });
    });
  });

  // ── Navigation ───────────────────────────────────────────────────────────────

  describe('redirectOnFailure$', () => {
    it('navigates to /accounts when loadAccountFailure is dispatched', (done) => {
      const router = TestBed.inject(Router);
      actions$ = of(AccountDetailActions.loadAccountFailure({ error: 'Not found' }));

      effects.redirectOnFailure$.subscribe(() => {
        expect(router.navigate).toHaveBeenCalledWith(['/accounts']);
        done();
      });
    });
  });
});
