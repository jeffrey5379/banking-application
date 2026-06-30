import { createSelector } from '@ngrx/store';
import { accountsFeature } from './accounts.reducer';

export const {
  selectAccountsState,
  selectAccounts,
  selectLoading: selectAccountsLoading,
  selectAddingAccount,
  selectError: selectAccountsError,
} = accountsFeature;

export const selectCurrencyTotals = createSelector(selectAccounts, (accounts) => {
  const map = new Map<string, number>();
  for (const a of accounts) {
    map.set(a.currency, (map.get(a.currency) ?? 0) + a.balance);
  }
  return Array.from(map.entries()).map(([currency, total]) => ({ currency, total }));
});
