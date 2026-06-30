import { createFeature, createReducer, on } from '@ngrx/store';
import { Account } from '../../models/bank.models';
import { AccountsActions } from './accounts.actions';

export interface AccountsState {
  accounts: Account[];
  loading: boolean;
  addingAccount: boolean;
  error: string | null;
}

const initialState: AccountsState = {
  accounts: [],
  loading: false,
  addingAccount: false,
  error: null,
};

export const accountsFeature = createFeature({
  name: 'accounts',
  reducer: createReducer(
    initialState,
    on(AccountsActions.loadAccounts, (state) => ({
      ...state,
      loading: true,
      error: null,
    })),
    on(AccountsActions.loadAccountsSuccess, (state, { accounts }) => ({
      ...state,
      accounts,
      loading: false,
    })),
    on(AccountsActions.loadAccountsFailure, (state, { error }) => ({
      ...state,
      loading: false,
      error,
    })),
    on(AccountsActions.createAccount, (state) => ({
      ...state,
      addingAccount: true,
      error: null,
    })),
    on(AccountsActions.createAccountSuccess, (state, { account }) => ({
      ...state,
      accounts: [...state.accounts, account],
      addingAccount: false,
    })),
    on(AccountsActions.createAccountFailure, (state, { error }) => ({
      ...state,
      addingAccount: false,
      error,
    })),
  ),
});
