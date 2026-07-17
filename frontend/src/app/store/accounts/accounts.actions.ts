import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Account, Currency } from '../../models/bank.models';

export const AccountsActions = createActionGroup({
  source: 'Accounts',
  events: {
    'Load Accounts': props<{ userId: string }>(),
    'Load Accounts Success': props<{ accounts: Account[] }>(),
    'Load Accounts Failure': props<{ error: string }>(),

    // Re-fetches the account list without disturbing loading/error UI state.
    // Used to resync with the backend after a failed createAccount.
    'Refresh Accounts': props<{ userId: string }>(),

    'Create Account': props<{ currency: Currency; idempotencyKey: string }>(),
    'Create Account Success': props<{ account: Account }>(),
    'Create Account Failure': props<{ error: string }>(),
  },
});
