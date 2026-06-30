import { createFeature, createReducer, on } from '@ngrx/store';
import { Transaction } from '../../models/bank.models';
import { TransactionActions } from './transaction.actions';

export interface TransactionState {
  transaction: Transaction | null;
  loading: boolean;
  error: string | null;
}

const initialState: TransactionState = {
  transaction: null,
  loading: false,
  error: null,
};

export const transactionFeature = createFeature({
  name: 'transaction',
  reducer: createReducer(
    initialState,
    on(TransactionActions.loadTransaction, (state) => ({
      ...state,
      loading: true,
      error: null,
    })),
    on(TransactionActions.loadTransactionSuccess, (state, { transaction }) => ({
      ...state,
      transaction,
      loading: false,
    })),
    on(TransactionActions.loadTransactionFailure, (state, { error }) => ({
      ...state,
      loading: false,
      error,
    })),
    on(TransactionActions.clear, () => initialState),
  ),
});
