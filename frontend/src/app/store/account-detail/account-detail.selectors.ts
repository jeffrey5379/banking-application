import { createSelector } from '@ngrx/store';
import { accountDetailFeature } from './account-detail.reducer';

export const {
  selectAccountDetailState,
  selectAccountId,
  selectAccount,
  selectAllAccounts,
  selectExchangeRates,
  selectTransactions,
  selectBalanceHistory,
  selectStats,
  selectTotalElements,
  selectCurrentPage,
  selectHasMore,
  selectLoading: selectAccountDetailLoading,
  selectLoadingMore,
  selectOperationLoading,
  selectError: selectAccountDetailError,
} = accountDetailFeature;

export const selectOtherAccounts = createSelector(
  selectAccount,
  selectAllAccounts,
  (account, allAccounts) =>
    allAccounts.filter((a) => a.id !== account?.id && a.currency !== account?.currency),
);
