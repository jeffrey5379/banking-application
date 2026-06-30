import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Transaction } from '../../models/bank.models';

export const TransactionActions = createActionGroup({
  source: 'Transaction',
  events: {
    'Load Transaction': props<{ id: number }>(),
    'Load Transaction Success': props<{ transaction: Transaction }>(),
    'Load Transaction Failure': props<{ error: string }>(),
    'Clear': emptyProps(),
  },
});
