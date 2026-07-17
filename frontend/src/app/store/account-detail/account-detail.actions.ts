import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Account, AccountStats, BalancePoint, ExchangeRequest, MoneyRequest, Transaction } from '../../models/bank.models';

export const AccountDetailActions = createActionGroup({
  source: 'AccountDetail',
  events: {
    'Load Account': props<{ id: string }>(),
    'Load Account Success': props<{ account: Account }>(),
    'Load Account Failure': props<{ error: string }>(),

    'Load All Accounts': props<{ userId: string }>(),
    'Load All Accounts Success': props<{ accounts: Account[] }>(),

    'Load Exchange Rates': emptyProps(),
    'Load Exchange Rates Success': props<{ rates: Record<string, number> }>(),

    'Load Transactions': props<{ accountId: string; page: number }>(),
    'Load Transactions Success': props<{ content: Transaction[]; page: number; totalElements: number; last: boolean }>(),
    'Load Transactions Failure': emptyProps(),

    'Load More Transactions': emptyProps(),

    'Load Balance History': props<{ accountId: string }>(),
    'Load Balance History Success': props<{ history: BalancePoint[] }>(),

    // Re-fetches just the account summary (balance) without disturbing loading/error UI state.
    // Used to resync with the backend after a failed credit/debit/exchange.
    'Refresh Account': props<{ accountId: string }>(),

    'Submit Credit': props<{ accountId: string; req: MoneyRequest; idempotencyKey: string }>(),
    'Submit Credit Success': props<{ transaction: Transaction }>(),
    'Submit Credit Failure': props<{ error: string }>(),

    'Submit Debit': props<{ accountId: string; req: MoneyRequest; idempotencyKey: string }>(),
    'Submit Debit Success': props<{ transaction: Transaction }>(),
    'Submit Debit Failure': props<{ error: string }>(),

    'Submit Exchange': props<{ accountId: string; req: ExchangeRequest; idempotencyKey: string }>(),
    'Submit Exchange Success': props<{ transactions: Transaction[] }>(),
    'Submit Exchange Failure': props<{ error: string }>(),

    'Load Summary': props<{ accountId: string }>(),
    'Load Summary Success': props<{ stats: AccountStats }>(),

    'Clear': emptyProps(),
  },
});
