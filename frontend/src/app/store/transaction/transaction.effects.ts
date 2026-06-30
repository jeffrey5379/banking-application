import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, map, of, switchMap } from 'rxjs';
import { BankService } from '../../services/bank.service';
import { TransactionActions } from './transaction.actions';

@Injectable()
export class TransactionEffects {
  private actions$ = inject(Actions);
  private bankService = inject(BankService);

  loadTransaction$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TransactionActions.loadTransaction),
      switchMap(({ id }) =>
        this.bankService.getTransaction(id).pipe(
          map((transaction) => TransactionActions.loadTransactionSuccess({ transaction })),
          catchError(() =>
            of(TransactionActions.loadTransactionFailure({ error: 'Could not load transaction.' })),
          ),
        ),
      ),
    ),
  );
}
