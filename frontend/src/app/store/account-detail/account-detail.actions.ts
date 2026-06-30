import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Account, AccountStats, BalancePoint, ExchangeRequest, MoneyRequest, Transaction } from '../../models/bank.models';

export const AccountDetailActions = createActionGroup({
  source: 'AccountDetail',
  events: {
    'Load Account': props<{ id: number }>(),
    'Load Account Success': props<{ account: Account }>(),
    'Load Account Failure': props<{ error: string }>(),

    'Load All Accounts': props<{ userId: number }>(),
    'Load All Accounts Success': props<{ accounts: Account[] }>(),

    'Load Exchange Rates': emptyProps(),
    'Load Exchange Rates Success': props<{ rates: Record<string, number> }>(),

    'Load Transactions': props<{ accountId: number; page: number }>(),
    'Load Transactions Success': props<{ content: Transaction[]; page: number; totalElements: number; last: boolean }>(),
    'Load Transactions Failure': emptyProps(),

    'Load More Transactions': emptyProps(),

    'Load Balance History': props<{ accountId: number }>(),
    'Load Balance History Success': props<{ history: BalancePoint[] }>(),

    'Submit Credit': props<{ accountId: number; req: MoneyRequest }>(),
    'Submit Credit Success': props<{ transaction: Transaction }>(),
    'Submit Credit Failure': props<{ error: string }>(),

    'Submit Debit': props<{ accountId: number; req: MoneyRequest }>(),
    'Submit Debit Success': props<{ transaction: Transaction }>(),
    'Submit Debit Failure': props<{ error: string }>(),

    'Submit Exchange': props<{ accountId: number; req: ExchangeRequest }>(),
    'Submit Exchange Success': props<{ transactions: Transaction[] }>(),
    'Submit Exchange Failure': props<{ error: string }>(),

    'Load Summary': props<{ accountId: number }>(),
    'Load Summary Success': props<{ stats: AccountStats }>(),

    'Clear': emptyProps(),
  },
});
