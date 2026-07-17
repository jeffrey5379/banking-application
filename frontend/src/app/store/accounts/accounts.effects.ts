import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of, switchMap } from 'rxjs';
import { BankService } from '../../services/bank.service';
import { AuthService } from '../../services/auth.service';
import { AccountsActions } from './accounts.actions';

@Injectable()
export class AccountsEffects {
  private actions$ = inject(Actions);
  private bankService = inject(BankService);
  private authService = inject(AuthService);

  loadAccounts$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountsActions.loadAccounts),
      switchMap(({ userId }) =>
        this.bankService.getAccountsByUser(userId).pipe(
          map((accounts) => AccountsActions.loadAccountsSuccess({ accounts })),
          catchError((e) =>
            of(AccountsActions.loadAccountsFailure({ error: e.error?.message ?? e.message ?? 'Could not load accounts.' })),
          ),
        ),
      ),
    ),
  );

  refreshAccounts$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountsActions.refreshAccounts),
      switchMap(({ userId }) =>
        this.bankService.getAccountsByUser(userId).pipe(
          map((accounts) => AccountsActions.loadAccountsSuccess({ accounts })),
          catchError(() => of()),
        ),
      ),
    ),
  );

  createAccount$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountsActions.createAccount),
      exhaustMap(({ currency, idempotencyKey }) =>
        this.bankService.createAccount({ currency }, idempotencyKey).pipe(
          map((account) => AccountsActions.createAccountSuccess({ account })),
          catchError((e) =>
            of(AccountsActions.createAccountFailure({ error: e.error?.message ?? e.message ?? 'Could not create account.' })),
          ),
        ),
      ),
    ),
  );

  // Resync with the backend after a failed createAccount - the server may have actually
  // committed the account even though the response was lost.
  resyncOnFailure$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountsActions.createAccountFailure),
      switchMap(() => {
        const userId = this.authService.getUser()?.userId;
        return userId ? of(AccountsActions.refreshAccounts({ userId })) : of();
      }),
    ),
  );
}
