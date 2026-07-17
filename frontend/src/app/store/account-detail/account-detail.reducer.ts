import { createFeature, createReducer, on } from '@ngrx/store';
import { Account, AccountStats, BalancePoint, Transaction } from '../../models/bank.models';
import { AccountDetailActions } from './account-detail.actions';

export interface AccountDetailState {
  accountId: string | null;
  account: Account | null;
  allAccounts: Account[];
  exchangeRates: Record<string, number>;
  transactions: Transaction[];
  balanceHistory: BalancePoint[];
  stats: AccountStats | null;
  totalElements: number;
  currentPage: number;
  hasMore: boolean;
  loading: boolean;
  loadingMore: boolean;
  operationLoading: boolean;
  error: string | null;
}

const initialState: AccountDetailState = {
  accountId: null,
  account: null,
  allAccounts: [],
  exchangeRates: {},
  transactions: [],
  balanceHistory: [],
  stats: null,
  totalElements: 0,
  currentPage: 0,
  hasMore: false,
  loading: false,
  loadingMore: false,
  operationLoading: false,
  error: null,
};

export const accountDetailFeature = createFeature({
  name: 'accountDetail',
  reducer: createReducer(
    initialState,
    on(AccountDetailActions.loadAccount, (state, { id }) => {
      // Same account already in store → keep data, background refresh without spinner
      if (state.accountId === id && state.account !== null) {
        return { ...state, error: null };
      }
      return { ...initialState, accountId: id, loading: true };
    }),
    on(AccountDetailActions.loadAccountSuccess, (state, { account }) => ({
      ...state,
      account,
      loading: false,
    })),
    on(AccountDetailActions.loadAccountFailure, (state, { error }) => ({
      ...state,
      loading: false,
      error,
    })),

    on(AccountDetailActions.loadAllAccountsSuccess, (state, { accounts }) => ({
      ...state,
      allAccounts: accounts,
    })),

    on(AccountDetailActions.loadExchangeRatesSuccess, (state, { rates }) => ({
      ...state,
      exchangeRates: rates,
    })),

    on(AccountDetailActions.loadTransactions, (state, { page }) => ({
      ...state,
      loadingMore: page > 0,
    })),
    on(AccountDetailActions.loadTransactionsSuccess, (state, { content, page, totalElements, last }) => {
      let transactions: Transaction[];
      if (page === 0) {
        transactions = content;
      } else {
        const seen = new Set(state.transactions.map((t) => t.id));
        const fresh = content.filter((t) => !seen.has(t.id));
        transactions = [...state.transactions, ...fresh];
      }
      return {
        ...state,
        transactions,
        totalElements,
        currentPage: page,
        hasMore: !last,
        loadingMore: false,
      };
    }),
    on(AccountDetailActions.loadTransactionsFailure, (state) => ({
      ...state,
      loadingMore: false,
    })),

    on(AccountDetailActions.loadBalanceHistorySuccess, (state, { history }) => ({
      ...state,
      balanceHistory: history,
    })),

    on(AccountDetailActions.loadSummarySuccess, (state, { stats }) => ({
      ...state,
      stats,
    })),

    on(AccountDetailActions.submitCredit, (state) => ({ ...state, operationLoading: true, error: null })),
    on(AccountDetailActions.submitCreditSuccess, (state, { transaction }) => ({
      ...state,
      operationLoading: false,
      transactions: [transaction, ...state.transactions],
      totalElements: state.totalElements + 1,
      account: state.account
        ? { ...state.account, balance: transaction.balanceAfter }
        : null,
    })),
    on(AccountDetailActions.submitCreditFailure, (state, { error }) => ({
      ...state,
      operationLoading: false,
      error,
    })),

    on(AccountDetailActions.submitDebit, (state) => ({ ...state, operationLoading: true, error: null })),
    on(AccountDetailActions.submitDebitSuccess, (state, { transaction }) => ({
      ...state,
      operationLoading: false,
      transactions: [transaction, ...state.transactions],
      totalElements: state.totalElements + 1,
      account: state.account
        ? { ...state.account, balance: transaction.balanceAfter }
        : null,
    })),
    on(AccountDetailActions.submitDebitFailure, (state, { error }) => ({
      ...state,
      operationLoading: false,
      error,
    })),

    on(AccountDetailActions.submitExchange, (state) => ({ ...state, operationLoading: true, error: null })),
    on(AccountDetailActions.submitExchangeSuccess, (state, { transactions }) => {
      const mine = transactions.filter((t) => t.accountId === state.accountId);
      const outgoing = mine.find((t) => t.type === 'EXCHANGE_OUT');
      return {
        ...state,
        operationLoading: false,
        transactions: [...mine, ...state.transactions],
        totalElements: state.totalElements + mine.length,
        account: state.account && outgoing
          ? { ...state.account, balance: outgoing.balanceAfter }
          : state.account,
      };
    }),
    on(AccountDetailActions.submitExchangeFailure, (state, { error }) => ({
      ...state,
      operationLoading: false,
      error,
    })),

    on(AccountDetailActions.clear, () => initialState),
  ),
});
