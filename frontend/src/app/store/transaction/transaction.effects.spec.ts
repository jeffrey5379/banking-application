import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of, throwError } from 'rxjs';
import { Action } from '@ngrx/store';
import { TransactionEffects } from './transaction.effects';
import { TransactionActions } from './transaction.actions';
import { BankService } from '../../services/bank.service';
import { Transaction } from '../../models/bank.models';

const mockTx: Transaction = {
  id: '42',
  accountId: '1',
  accountNumber: 'ACC-001',
  type: 'DEBIT',
  amount: 50,
  currency: 'EUR',
  balanceAfter: 950,
  description: 'Rent',
  createdAt: '2024-05-01T00:00:00Z',
};

describe('TransactionEffects', () => {
  let actions$: Observable<Action>;
  let effects: TransactionEffects;
  let bankService: jest.Mocked<Pick<BankService, 'getTransaction'>>;

  beforeEach(() => {
    bankService = { getTransaction: jest.fn() };

    TestBed.configureTestingModule({
      providers: [
        TransactionEffects,
        provideMockActions(() => actions$),
        { provide: BankService, useValue: bankService },
      ],
    });
    effects = TestBed.inject(TransactionEffects);
  });

  it('dispatches loadTransactionSuccess on API success', (done) => {
    bankService.getTransaction.mockReturnValue(of(mockTx));
    actions$ = of(TransactionActions.loadTransaction({ id: '42' }));

    effects.loadTransaction$.subscribe((action) => {
      expect(action).toEqual(TransactionActions.loadTransactionSuccess({ transaction: mockTx }));
      done();
    });
  });

  it('dispatches loadTransactionFailure on API error', (done) => {
    bankService.getTransaction.mockReturnValue(throwError(() => new Error('Not found')));
    actions$ = of(TransactionActions.loadTransaction({ id: '99' }));

    effects.loadTransaction$.subscribe((action) => {
      expect(action).toEqual(
        TransactionActions.loadTransactionFailure({ error: 'Could not load transaction.' }),
      );
      done();
    });
  });
});
