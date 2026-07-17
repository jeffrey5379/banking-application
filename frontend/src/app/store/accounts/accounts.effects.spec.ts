import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of, throwError } from 'rxjs';
import { Action } from '@ngrx/store';
import { AccountsEffects } from './accounts.effects';
import { AccountsActions } from './accounts.actions';
import { BankService } from '../../services/bank.service';
import { AuthService } from '../../services/auth.service';
import { Account } from '../../models/bank.models';

const mockAccount: Account = {
  id: '1',
  accountNumber: 'ACC-001',
  currency: 'EUR',
  balance: 100,
  userId: '1',
  username: 'alice',
};

describe('AccountsEffects', () => {
  let actions$: Observable<Action>;
  let effects: AccountsEffects;
  let bankService: jest.Mocked<Pick<BankService, 'getAccountsByUser' | 'createAccount'>>;
  let authService: jest.Mocked<Pick<AuthService, 'getUser'>>;

  beforeEach(() => {
    bankService = { getAccountsByUser: jest.fn(), createAccount: jest.fn() };
    authService = { getUser: jest.fn() };

    TestBed.configureTestingModule({
      providers: [
        AccountsEffects,
        provideMockActions(() => actions$),
        { provide: BankService, useValue: bankService },
        { provide: AuthService, useValue: authService },
      ],
    });
    effects = TestBed.inject(AccountsEffects);
  });

  describe('loadAccounts$', () => {
    it('dispatches loadAccountsSuccess on API success', (done) => {
      bankService.getAccountsByUser.mockReturnValue(of([mockAccount]));
      actions$ = of(AccountsActions.loadAccounts({ userId: '1' }));

      effects.loadAccounts$.subscribe((action) => {
        expect(action).toEqual(AccountsActions.loadAccountsSuccess({ accounts: [mockAccount] }));
        done();
      });
    });

    it('propagates the backend error message on failure', (done) => {
      bankService.getAccountsByUser.mockReturnValue(
        throwError(() => ({ error: { message: 'Unauthorized' }, message: 'Http failure' })),
      );
      actions$ = of(AccountsActions.loadAccounts({ userId: '1' }));

      effects.loadAccounts$.subscribe((action) => {
        expect(action).toEqual(AccountsActions.loadAccountsFailure({ error: 'Unauthorized' }));
        done();
      });
    });

    it('falls back to e.message when error.message is absent', (done) => {
      bankService.getAccountsByUser.mockReturnValue(
        throwError(() => ({ message: 'Network error' })),
      );
      actions$ = of(AccountsActions.loadAccounts({ userId: '1' }));

      effects.loadAccounts$.subscribe((action) => {
        expect(action).toEqual(
          AccountsActions.loadAccountsFailure({ error: 'Network error' }),
        );
        done();
      });
    });

    it('uses the hardcoded fallback when no error message is available', (done) => {
      bankService.getAccountsByUser.mockReturnValue(throwError(() => ({})));
      actions$ = of(AccountsActions.loadAccounts({ userId: '1' }));

      effects.loadAccounts$.subscribe((action) => {
        expect(action).toEqual(
          AccountsActions.loadAccountsFailure({ error: 'Could not load accounts.' }),
        );
        done();
      });
    });
  });

  describe('createAccount$', () => {
    it('dispatches createAccountSuccess on success', (done) => {
      bankService.createAccount.mockReturnValue(of(mockAccount));
      actions$ = of(AccountsActions.createAccount({ currency: 'EUR', idempotencyKey: 'idem-key-1' }));

      effects.createAccount$.subscribe((action) => {
        expect(action).toEqual(AccountsActions.createAccountSuccess({ account: mockAccount }));
        done();
      });
    });

    it('sends the idempotency key through to the backend', (done) => {
      bankService.createAccount.mockReturnValue(of(mockAccount));
      actions$ = of(AccountsActions.createAccount({ currency: 'EUR', idempotencyKey: 'idem-key-1' }));

      effects.createAccount$.subscribe(() => {
        expect(bankService.createAccount).toHaveBeenCalledWith({ currency: 'EUR' }, 'idem-key-1');
        done();
      });
    });

    it('dispatches createAccountFailure with backend error', (done) => {
      bankService.createAccount.mockReturnValue(
        throwError(() => ({ error: { message: 'Currency already exists' } })),
      );
      actions$ = of(AccountsActions.createAccount({ currency: 'EUR', idempotencyKey: 'idem-key-1' }));

      effects.createAccount$.subscribe((action) => {
        expect(action).toEqual(
          AccountsActions.createAccountFailure({ error: 'Currency already exists' }),
        );
        done();
      });
    });
  });

  describe('refreshAccounts$', () => {
    it('dispatches loadAccountsSuccess on API success', (done) => {
      bankService.getAccountsByUser.mockReturnValue(of([mockAccount]));
      actions$ = of(AccountsActions.refreshAccounts({ userId: '1' }));

      effects.refreshAccounts$.subscribe((action) => {
        expect(action).toEqual(AccountsActions.loadAccountsSuccess({ accounts: [mockAccount] }));
        done();
      });
    });

    it('emits nothing on API failure', (done) => {
      bankService.getAccountsByUser.mockReturnValue(throwError(() => ({})));
      actions$ = of(AccountsActions.refreshAccounts({ userId: '1' }));

      const emitted: Action[] = [];
      effects.refreshAccounts$.subscribe({
        next: (a) => emitted.push(a),
        complete: () => {
          expect(emitted).toHaveLength(0);
          done();
        },
      });
    });
  });

  describe('resyncOnFailure$', () => {
    it('dispatches refreshAccounts with the current user id after createAccountFailure', (done) => {
      authService.getUser.mockReturnValue({ userId: '1', username: 'alice' });
      actions$ = of(AccountsActions.createAccountFailure({ error: 'Could not create account.' }));

      effects.resyncOnFailure$.subscribe((action) => {
        expect(action).toEqual(AccountsActions.refreshAccounts({ userId: '1' }));
        done();
      });
    });

    it('emits nothing when there is no authenticated user', (done) => {
      authService.getUser.mockReturnValue(null);
      actions$ = of(AccountsActions.createAccountFailure({ error: 'Could not create account.' }));

      const emitted: Action[] = [];
      effects.resyncOnFailure$.subscribe({
        next: (a) => emitted.push(a),
        complete: () => {
          expect(emitted).toHaveLength(0);
          done();
        },
      });
    });
  });
});
