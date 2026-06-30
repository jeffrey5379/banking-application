import { AccountDetailActions } from './account-detail.actions';
import { accountDetailFeature, AccountDetailState } from './account-detail.reducer';
import { AccountDetail, Transaction } from '../../models/bank.models';

const reducer = accountDetailFeature.reducer;

const blankState = (): AccountDetailState =>
  reducer(undefined, { type: '@@INIT' } as any);

const mockAccount: AccountDetail = {
  id: 1,
  accountNumber: 'ACC-001',
  currency: 'EUR',
  balance: 1000,
  userId: 1,
  username: 'alice',
  transactions: [],
};

const tx = (id: number, accountId = 1, type: Transaction['type'] = 'CREDIT'): Transaction => ({
  id,
  accountId,
  accountNumber: 'ACC-001',
  type,
  amount: 100,
  currency: 'EUR',
  balanceAfter: 1000 + id,
  description: `tx-${id}`,
  createdAt: '2024-01-01T00:00:00Z',
});

describe('AccountDetailReducer', () => {
  describe('loadAccount', () => {
    it('resets state and sets loading when navigating to a new account', () => {
      const state = reducer(undefined, AccountDetailActions.loadAccount({ id: 5 }));
      expect(state.accountId).toBe(5);
      expect(state.loading).toBe(true);
      expect(state.account).toBeNull();
      expect(state.transactions).toEqual([]);
    });

    it('keeps existing data without spinner when the same account is already cached', () => {
      const cached: AccountDetailState = {
        ...blankState(),
        accountId: 1,
        account: mockAccount,
        transactions: [tx(10)],
        loading: false,
      };
      const state = reducer(cached, AccountDetailActions.loadAccount({ id: 1 }));
      expect(state.account).toEqual(mockAccount);
      expect(state.loading).toBe(false);
      expect(state.transactions).toHaveLength(1);
    });

    it('resets state when switching to a different account even if one is already loaded', () => {
      const cached: AccountDetailState = {
        ...blankState(),
        accountId: 1,
        account: mockAccount,
        loading: false,
      };
      const state = reducer(cached, AccountDetailActions.loadAccount({ id: 2 }));
      expect(state.accountId).toBe(2);
      expect(state.account).toBeNull();
      expect(state.loading).toBe(true);
    });
  });

  describe('loadAccountSuccess', () => {
    it('stores account and clears loading', () => {
      const state = reducer(
        { ...blankState(), loading: true },
        AccountDetailActions.loadAccountSuccess({ account: mockAccount }),
      );
      expect(state.account).toEqual(mockAccount);
      expect(state.loading).toBe(false);
    });
  });

  describe('loadAccountFailure', () => {
    it('stores error and clears loading', () => {
      const state = reducer(
        { ...blankState(), loading: true },
        AccountDetailActions.loadAccountFailure({ error: 'Not found' }),
      );
      expect(state.error).toBe('Not found');
      expect(state.loading).toBe(false);
    });
  });

  describe('loadTransactionsSuccess', () => {
    it('replaces transactions on page 0', () => {
      const existing: AccountDetailState = {
        ...blankState(),
        transactions: [tx(99)],
      };
      const state = reducer(
        existing,
        AccountDetailActions.loadTransactionsSuccess({
          content: [tx(1), tx(2)],
          page: 0,
          totalElements: 2,
          last: true,
        }),
      );
      expect(state.transactions.map((t) => t.id)).toEqual([1, 2]);
      expect(state.hasMore).toBe(false);
      expect(state.totalElements).toBe(2);
    });

    it('appends new transactions on subsequent pages', () => {
      const existing: AccountDetailState = {
        ...blankState(),
        transactions: [tx(1), tx(2)],
        currentPage: 0,
      };
      const state = reducer(
        existing,
        AccountDetailActions.loadTransactionsSuccess({
          content: [tx(3), tx(4)],
          page: 1,
          totalElements: 4,
          last: true,
        }),
      );
      expect(state.transactions.map((t) => t.id)).toEqual([1, 2, 3, 4]);
      expect(state.currentPage).toBe(1);
    });

    it('deduplicates transactions on page > 0', () => {
      const existing: AccountDetailState = {
        ...blankState(),
        transactions: [tx(1), tx(2)],
        currentPage: 0,
      };
      const state = reducer(
        existing,
        AccountDetailActions.loadTransactionsSuccess({
          content: [tx(2), tx(3)], // tx(2) is a duplicate
          page: 1,
          totalElements: 3,
          last: false,
        }),
      );
      expect(state.transactions.map((t) => t.id)).toEqual([1, 2, 3]);
    });
  });

  describe('submitCreditSuccess', () => {
    it('prepends transaction, updates balance, and increments totalElements', () => {
      const existing: AccountDetailState = {
        ...blankState(),
        accountId: 1,
        account: { ...mockAccount, balance: 1000 },
        transactions: [tx(5)],
        totalElements: 1,
      };
      const creditTx = { ...tx(10), balanceAfter: 1100 };
      const state = reducer(existing, AccountDetailActions.submitCreditSuccess({ transaction: creditTx }));
      expect(state.transactions[0]).toEqual(creditTx);
      expect(state.transactions).toHaveLength(2);
      expect(state.account?.balance).toBe(1100);
      expect(state.totalElements).toBe(2);
      expect(state.operationLoading).toBe(false);
    });
  });

  describe('submitDebitSuccess', () => {
    it('prepends transaction and updates balance', () => {
      const existing: AccountDetailState = {
        ...blankState(),
        accountId: 1,
        account: { ...mockAccount, balance: 1000 },
        transactions: [],
        totalElements: 0,
      };
      const debitTx = { ...tx(20), type: 'DEBIT' as const, balanceAfter: 900 };
      const state = reducer(existing, AccountDetailActions.submitDebitSuccess({ transaction: debitTx }));
      expect(state.account?.balance).toBe(900);
      expect(state.transactions[0]).toEqual(debitTx);
      expect(state.operationLoading).toBe(false);
    });
  });

  describe('submitExchangeSuccess', () => {
    it('filters to only transactions belonging to the current account', () => {
      const existing: AccountDetailState = {
        ...blankState(),
        accountId: 1,
        account: { ...mockAccount, balance: 1000 },
        transactions: [],
        totalElements: 0,
      };
      const myTx = tx(10, 1, 'EXCHANGE_OUT');
      const otherTx = tx(11, 2, 'EXCHANGE_IN');
      const state = reducer(
        existing,
        AccountDetailActions.submitExchangeSuccess({ transactions: [myTx, otherTx] }),
      );
      expect(state.transactions).toHaveLength(1);
      expect(state.transactions[0].accountId).toBe(1);
    });

    it('updates balance from the EXCHANGE_OUT transaction', () => {
      const existing: AccountDetailState = {
        ...blankState(),
        accountId: 1,
        account: { ...mockAccount, balance: 1000 },
        transactions: [],
        totalElements: 0,
      };
      const outTx = { ...tx(10, 1, 'EXCHANGE_OUT'), balanceAfter: 750 };
      const state = reducer(
        existing,
        AccountDetailActions.submitExchangeSuccess({ transactions: [outTx] }),
      );
      expect(state.account?.balance).toBe(750);
    });
  });

  describe('submitCreditFailure / submitDebitFailure / submitExchangeFailure', () => {
    it.each([
      ['credit', AccountDetailActions.submitCreditFailure({ error: 'Credit failed' })],
      ['debit', AccountDetailActions.submitDebitFailure({ error: 'Debit failed' })],
      ['exchange', AccountDetailActions.submitExchangeFailure({ error: 'Exchange failed' })],
    ])('%s failure stores error and clears operationLoading', (_label, action) => {
      const state = reducer({ ...blankState(), operationLoading: true }, action);
      expect(state.operationLoading).toBe(false);
      expect(state.error).toContain('failed');
    });
  });

  describe('clear', () => {
    it('resets the entire state to initial', () => {
      const loaded: AccountDetailState = {
        ...blankState(),
        accountId: 1,
        account: mockAccount,
        transactions: [tx(1), tx(2)],
        exchangeRates: { EUR_USD: 0.92 },
      };
      const state = reducer(loaded, AccountDetailActions.clear());
      expect(state.account).toBeNull();
      expect(state.accountId).toBeNull();
      expect(state.transactions).toEqual([]);
      expect(state.exchangeRates).toEqual({});
    });
  });
});
