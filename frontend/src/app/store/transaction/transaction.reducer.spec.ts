import { TransactionActions } from './transaction.actions';
import { transactionFeature } from './transaction.reducer';
import { Transaction } from '../../models/bank.models';

const reducer = transactionFeature.reducer;

const mockTx: Transaction = {
  id: 42,
  accountId: 1,
  accountNumber: 'ACC-001',
  type: 'CREDIT',
  amount: 200,
  currency: 'EUR',
  balanceAfter: 1200,
  description: 'Salary',
  createdAt: '2024-06-01T12:00:00Z',
};

describe('TransactionReducer', () => {
  it('returns initial state', () => {
    const state = reducer(undefined, { type: '@@INIT' } as any);
    expect(state).toEqual({ transaction: null, loading: false, error: null });
  });

  it('loadTransaction sets loading and clears error', () => {
    const state = reducer(
      { transaction: null, loading: false, error: 'old' },
      TransactionActions.loadTransaction({ id: 42 }),
    );
    expect(state.loading).toBe(true);
    expect(state.error).toBeNull();
  });

  it('loadTransactionSuccess stores transaction and clears loading', () => {
    const state = reducer(
      { transaction: null, loading: true, error: null },
      TransactionActions.loadTransactionSuccess({ transaction: mockTx }),
    );
    expect(state.transaction).toEqual(mockTx);
    expect(state.loading).toBe(false);
  });

  it('loadTransactionFailure stores error and clears loading', () => {
    const state = reducer(
      { transaction: null, loading: true, error: null },
      TransactionActions.loadTransactionFailure({ error: 'Not found' }),
    );
    expect(state.error).toBe('Not found');
    expect(state.loading).toBe(false);
    expect(state.transaction).toBeNull();
  });

  it('clear resets state', () => {
    const state = reducer(
      { transaction: mockTx, loading: false, error: null },
      TransactionActions.clear(),
    );
    expect(state.transaction).toBeNull();
    expect(state.error).toBeNull();
  });
});
