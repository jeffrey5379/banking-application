import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Account, Currency } from '../../models/bank.models';

export const AccountsActions = createActionGroup({
  source: 'Accounts',
  events: {
    'Load Accounts': props<{ userId: number }>(),
    'Load Accounts Success': props<{ accounts: Account[] }>(),
    'Load Accounts Failure': props<{ error: string }>(),
    'Create Account': props<{ currency: Currency }>(),
    'Create Account Success': props<{ account: Account }>(),
    'Create Account Failure': props<{ error: string }>(),
  },
});
