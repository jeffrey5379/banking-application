import { transactionFeature } from './transaction.reducer';

export const {
  selectTransactionState,
  selectTransaction,
  selectLoading: selectTransactionLoading,
  selectError: selectTransactionError,
} = transactionFeature;
