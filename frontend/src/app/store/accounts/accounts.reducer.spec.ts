import { AccountsActions } from './accounts.actions';
import { accountsFeature, AccountsState } from './accounts.reducer';
import { Account } from '../../models/bank.models';

const reducer = accountsFeature.reducer;

const mockAccount: Account = {
  id: '1',
  accountNumber: 'ACC-001',
  currency: 'EUR',
  balance: 1000,
  userId: '1',
  username: 'alice',
};

const initialState: AccountsState = {
  accounts: [],
  loading: false,
  addingAccount: false,
  error: null,
};

describe('AccountsReducer', () => {
  it('returns initial state for unknown action', () => {
    const state = reducer(undefined, { type: '@@INIT' } as any);
    expect(state).toEqual(initialState);
  });

  describe('loadAccounts', () => {
    it('sets loading and clears error', () => {
      const state = reducer(
        { ...initialState, error: 'previous error' },
        AccountsActions.loadAccounts({ userId: '1' }),
      );
      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });
  });

  describe('loadAccountsSuccess', () => {
    it('stores accounts and clears loading', () => {
      const state = reducer(
        { ...initialState, loading: true },
        AccountsActions.loadAccountsSuccess({ accounts: [mockAccount] }),
      );
      expect(state.accounts).toEqual([mockAccount]);
      expect(state.loading).toBe(false);
    });
  });

  describe('loadAccountsFailure', () => {
    it('stores error and clears loading', () => {
      const state = reducer(
        { ...initialState, loading: true },
        AccountsActions.loadAccountsFailure({ error: 'Network error' }),
      );
      expect(state.error).toBe('Network error');
      expect(state.loading).toBe(false);
    });
  });

  describe('createAccount', () => {
    it('sets addingAccount and clears error', () => {
      const state = reducer(
        { ...initialState, error: 'old error' },
        AccountsActions.createAccount({ currency: 'USD', idempotencyKey: 'idem-key-1' }),
      );
      expect(state.addingAccount).toBe(true);
      expect(state.error).toBeNull();
    });
  });

  describe('createAccountSuccess', () => {
    it('appends new account to list and clears addingAccount', () => {
      const newAccount: Account = { ...mockAccount, id: '2', currency: 'USD' };
      const state = reducer(
        { ...initialState, accounts: [mockAccount], addingAccount: true },
        AccountsActions.createAccountSuccess({ account: newAccount }),
      );
      expect(state.accounts).toHaveLength(2);
      expect(state.accounts[1]).toEqual(newAccount);
      expect(state.addingAccount).toBe(false);
    });
  });

  describe('createAccountFailure', () => {
    it('stores error and clears addingAccount', () => {
      const state = reducer(
        { ...initialState, addingAccount: true },
        AccountsActions.createAccountFailure({ error: 'Currency already exists' }),
      );
      expect(state.error).toBe('Currency already exists');
      expect(state.addingAccount).toBe(false);
    });
  });
});
