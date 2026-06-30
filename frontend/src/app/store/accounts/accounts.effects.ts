import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, map, of, switchMap } from 'rxjs';
import { BankService } from '../../services/bank.service';
import { AccountsActions } from './accounts.actions';

@Injectable()
export class AccountsEffects {
  private actions$ = inject(Actions);
  private bankService = inject(BankService);

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

  createAccount$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AccountsActions.createAccount),
      switchMap(({ currency }) =>
        this.bankService.createAccount({ currency }).pipe(
          map((account) => AccountsActions.createAccountSuccess({ account })),
          catchError((e) =>
            of(AccountsActions.createAccountFailure({ error: e.error?.message ?? e.message ?? 'Could not create account.' })),
          ),
        ),
      ),
    ),
  );
}
