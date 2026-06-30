import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { catchError, filter, map, of, switchMap, withLatestFrom } from 'rxjs';
import { BankService } from '../../services/bank.service';
import { AuthService } from '../../services/auth.service';
import { AccountDetailActions } from './account-detail.actions';
import { selectAccountId, selectCurrentPage, selectExchangeRates } from './account-detail.selectors';

const PAGE_SIZE = 15;

@Injectable()
export class AccountDetailEffects {
  private actions$ = inject(Actions);
  private bankService = inject(BankService);
  private authService = inject(AuthService);
  private store = inject(Store);

  loadAccount$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountDetailActions.loadAccount),
      switchMap(({ id }) =>
        this.bankService.getAccountSummary(id).pipe(
          map((account) => AccountDetailActions.loadAccountSuccess({ account })),
          catchError(() =>
            of(AccountDetailActions.loadAccountFailure({ error: 'Could not load account.' })),
          ),
        ),
      ),
    ),
  );

  loadAccountSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountDetailActions.loadAccountSuccess),
      switchMap(({ account }) => {
        const user = this.authService.getUser();
        const userId = user?.userId;
        const actions = [
          AccountDetailActions.loadTransactions({ accountId: account.id, page: 0 }),
          AccountDetailActions.loadBalanceHistory({ accountId: account.id }),
          AccountDetailActions.loadSummary({ accountId: account.id }),
          AccountDetailActions.loadExchangeRates(),
        ];
        if (userId) {
          actions.push(AccountDetailActions.loadAllAccounts({ userId }) as any);
        }
        return actions;
      }),
    ),
  );

  loadAllAccounts$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountDetailActions.loadAllAccounts),
      switchMap(({ userId }) =>
        this.bankService.getAccountsByUser(userId).pipe(
          map((accounts) => AccountDetailActions.loadAllAccountsSuccess({ accounts })),
          catchError(() => of()),
        ),
      ),
    ),
  );

  loadExchangeRates$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountDetailActions.loadExchangeRates),
      withLatestFrom(this.store.select(selectExchangeRates)),
      // Skip if rates are already in the store — they won't change during the session
      filter(([, rates]) => Object.keys(rates).length === 0),
      switchMap(() =>
        this.bankService.getExchangeRates().pipe(
          map((rates) => AccountDetailActions.loadExchangeRatesSuccess({ rates })),
          catchError(() => of()),
        ),
      ),
    ),
  );

  loadTransactions$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountDetailActions.loadTransactions),
      switchMap(({ accountId, page }) =>
        this.bankService.getTransactionsPaged(accountId, page, PAGE_SIZE).pipe(
          map(({ content, page: p, totalElements, last }) =>
            AccountDetailActions.loadTransactionsSuccess({ content, page: p, totalElements, last }),
          ),
          catchError(() => of(AccountDetailActions.loadTransactionsFailure())),
        ),
      ),
    ),
  );

  loadMoreTransactions$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountDetailActions.loadMoreTransactions),
      withLatestFrom(
        this.store.select(selectAccountId),
        this.store.select(selectCurrentPage),
      ),
      switchMap(([, accountId, currentPage]) => {
        if (!accountId) return of();
        return of(
          AccountDetailActions.loadTransactions({ accountId, page: currentPage + 1 }),
        );
      }),
    ),
  );

  loadBalanceHistory$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountDetailActions.loadBalanceHistory),
      switchMap(({ accountId }) =>
        this.bankService.getBalanceHistory(accountId).pipe(
          map((history) => AccountDetailActions.loadBalanceHistorySuccess({ history })),
          catchError(() => of()),
        ),
      ),
    ),
  );

  submitCredit$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountDetailActions.submitCredit),
      switchMap(({ accountId, req }) =>
        this.bankService.credit(accountId, req).pipe(
          map((transaction) => AccountDetailActions.submitCreditSuccess({ transaction })),
          catchError((e) =>
            of(AccountDetailActions.submitCreditFailure({ error: e.error?.message ?? e.message ?? 'Credit operation failed.' })),
          ),
        ),
      ),
    ),
  );

  submitDebit$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountDetailActions.submitDebit),
      switchMap(({ accountId, req }) =>
        this.bankService.debit(accountId, req).pipe(
          map((transaction) => AccountDetailActions.submitDebitSuccess({ transaction })),
          catchError((e) =>
            of(AccountDetailActions.submitDebitFailure({ error: e.error?.message ?? e.message ?? 'Debit operation failed.' })),
          ),
        ),
      ),
    ),
  );

  submitExchange$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountDetailActions.submitExchange),
      switchMap(({ accountId, req }) =>
        this.bankService.exchange(accountId, req).pipe(
          map((transactions) => AccountDetailActions.submitExchangeSuccess({ transactions })),
          catchError((e) =>
            of(AccountDetailActions.submitExchangeFailure({ error: e.error?.message ?? e.message ?? 'Exchange operation failed.' })),
          ),
        ),
      ),
    ),
  );

  refreshBalanceHistory$ = createEffect(() =>
    this.actions$.pipe(
      ofType(
        AccountDetailActions.submitCreditSuccess,
        AccountDetailActions.submitDebitSuccess,
        AccountDetailActions.submitExchangeSuccess,
      ),
      withLatestFrom(this.store.select(selectAccountId)),
      switchMap(([, accountId]) => {
        if (!accountId) return of();
        return of(AccountDetailActions.loadBalanceHistory({ accountId }));
      }),
    ),
  );

  loadSummary$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountDetailActions.loadSummary),
      switchMap(({ accountId }) =>
        this.bankService.getAccountStats(accountId).pipe(
          map((stats) => AccountDetailActions.loadSummarySuccess({ stats })),
          catchError(() => of()),
        ),
      ),
    ),
  );

  refreshStats$ = createEffect(() =>
    this.actions$.pipe(
      ofType(
        AccountDetailActions.submitCreditSuccess,
        AccountDetailActions.submitDebitSuccess,
        AccountDetailActions.submitExchangeSuccess,
      ),
      withLatestFrom(this.store.select(selectAccountId)),
      switchMap(([, accountId]) => {
        if (!accountId) return of();
        return of(AccountDetailActions.loadSummary({ accountId }));
      }),
    ),
  );
}
