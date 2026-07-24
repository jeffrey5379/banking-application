import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { Actions } from '@ngrx/effects';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { Subject, of, throwError } from 'rxjs';
import { AccountOverviewComponent } from './account-overview.component';
import { BankService } from '../../services/bank.service';
import { AccountDetailActions } from '../../store/account-detail/account-detail.actions';
import { AccountDetailState } from '../../store/account-detail/account-detail.reducer';
import {
  selectAccount,
  selectOtherAccounts,
  selectExchangeRates,
} from '../../store/account-detail/account-detail.selectors';
import { Account, Transaction } from '../../models/bank.models';

// jsdom has no IntersectionObserver; the component's infinite-scroll sentinel constructs one
// as soon as the template renders (@ViewChild setter), so any test calling detectChanges()
// needs this stubbed out first.
class MockIntersectionObserver {
  observe = jest.fn();
  unobserve = jest.fn();
  disconnect = jest.fn();
  takeRecords = () => [];
  root = null;
  rootMargin = '';
  thresholds: number[] = [];
}
(globalThis as unknown as { IntersectionObserver: unknown }).IntersectionObserver =
  MockIntersectionObserver;

// jsdom's crypto doesn't implement randomUUID(); the component calls it directly in its
// constructor (idempotency key generation), so it must exist before any test creates one.
if (!globalThis.crypto.randomUUID) {
  let counter = 0;
  (globalThis.crypto as { randomUUID: () => string }).randomUUID = () =>
    `test-uuid-${++counter}` as unknown as ReturnType<Crypto['randomUUID']>;
}

const initialAccountDetailState: AccountDetailState = {
  accountId: null,
  account: null,
  allAccounts: [],
  exchangeRates: {},
  transactions: [],
  balanceHistory: [],
  stats: null,
  totalElements: 0,
  currentPage: 0,
  hasMore: false,
  loading: false,
  loadingMore: false,
  operationLoading: false,
  error: null,
};

const mockAccount: Account = {
  id: '1',
  accountNumber: 'ACC-001',
  currency: 'EUR',
  balance: 1000,
  userId: '10',
  username: 'alice',
};

const mockTx: Transaction = {
  id: 'tx-1',
  accountId: '1',
  accountNumber: 'ACC-001',
  type: 'CREDIT',
  amount: 100,
  currency: 'EUR',
  balanceAfter: 1100,
  description: 'Test',
  createdAt: '2024-01-01T00:00:00Z',
};

describe('AccountOverviewComponent', () => {
  let component: AccountOverviewComponent;
  let fixture: ComponentFixture<AccountOverviewComponent>;
  let store: MockStore;
  let bankService: jest.Mocked<Pick<BankService, 'checkRecipient'>>;
  let router: { navigate: jest.Mock };
  let actions$: Subject<unknown>;

  beforeEach(() => {
    bankService = { checkRecipient: jest.fn() };
    router = { navigate: jest.fn() };
    actions$ = new Subject();

    TestBed.configureTestingModule({
      imports: [AccountOverviewComponent],
      providers: [
        provideMockStore({ initialState: { accountDetail: initialAccountDetailState } }),
        { provide: BankService, useValue: bankService },
        { provide: Actions, useValue: actions$ },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
        { provide: Router, useValue: router },
      ],
    });

    store = TestBed.inject(MockStore);
    store.overrideSelector(selectAccount, mockAccount);
    store.overrideSelector(selectOtherAccounts, []);
    store.overrideSelector(selectExchangeRates, {});
    store.refreshState();
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(AccountOverviewComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => store.resetSelectors());

  describe('ngOnInit', () => {
    it('dispatches loadAccount with the id from the route', () => {
      component.ngOnInit();
      expect(store.dispatch).toHaveBeenCalledWith(AccountDetailActions.loadAccount({ id: '1' }));
    });

    it.each([
      ['submitExchangeSuccess', AccountDetailActions.submitExchangeSuccess({ transactions: [mockTx] })],
      ['submitTransferSuccess', AccountDetailActions.submitTransferSuccess({ transactions: [mockTx] })],
    ])('closes the active modal when %s is dispatched', (_label, action) => {
      component.ngOnInit();
      component.activeModal = 'exchange';

      actions$.next(action);

      expect(component.activeModal).toBeNull();
    });

    it('does not close the modal on a *Failure action, so a retry can reuse it', () => {
      component.ngOnInit();
      component.activeModal = 'exchange';

      actions$.next(AccountDetailActions.submitExchangeFailure({ error: 'Insufficient funds' }));

      expect(component.activeModal).toBe('exchange');
    });
  });

  describe('ngOnDestroy', () => {
    it('dispatches clear', () => {
      component.ngOnDestroy();
      expect(store.dispatch).toHaveBeenCalledWith(AccountDetailActions.clear());
    });
  });

  describe('openModal / closeModal', () => {
    it('sets the active modal and resets all form fields', () => {
      component.modalAmount = 50;
      component.modalDesc = 'stale';
      component.targetAccountId = 'stale-id';
      component.transferUsername = 'stale-user';
      component.transferAccountNumber = 'stale-acc';

      component.openModal('transfer');

      expect(component.activeModal).toBe('transfer');
      expect(component.modalAmount).toBeNull();
      expect(component.modalDesc).toBe('');
      expect(component.targetAccountId).toBeNull();
      expect(component.transferUsername).toBe('');
      expect(component.transferAccountNumber).toBe('');
      expect(component.recipientStatus()).toBe('idle');
    });

    it('closeModal clears the active modal', () => {
      component.openModal('exchange');
      component.closeModal();
      expect(component.activeModal).toBeNull();
    });

    it('reuses the same idempotency key across multiple submits within one modal session (retry)', () => {
      component.openModal('exchange');
      component.modalAmount = 100;
      component.targetAccountId = '2';

      component.submitExchange();
      const firstKey = (store.dispatch as jest.Mock).mock.calls.at(-1)![0].idempotencyKey;

      component.submitExchange();
      const secondKey = (store.dispatch as jest.Mock).mock.calls.at(-1)![0].idempotencyKey;

      expect(firstKey).toBe(secondKey);
    });

    it('generates a new idempotency key each time the modal is (re)opened', () => {
      component.openModal('exchange');
      component.modalAmount = 100;
      component.targetAccountId = '2';
      component.submitExchange();
      const firstKey = (store.dispatch as jest.Mock).mock.calls.at(-1)![0].idempotencyKey;

      component.openModal('exchange');
      component.modalAmount = 100;
      component.targetAccountId = '2';
      component.submitExchange();
      const secondKey = (store.dispatch as jest.Mock).mock.calls.at(-1)![0].idempotencyKey;

      expect(firstKey).not.toBe(secondKey);
    });
  });

  describe('submitExchange', () => {
    it('does nothing without a target account', () => {
      component.openModal('exchange');
      component.modalAmount = 100;
      component.submitExchange();
      expect(store.dispatch).not.toHaveBeenCalledWith(expect.objectContaining({ type: expect.stringContaining('Submit Exchange') }));
    });

    it('dispatches submitExchange with the source account id and target', () => {
      component.openModal('exchange');
      component.modalAmount = 100;
      component.targetAccountId = '2';

      component.submitExchange();

      expect(store.dispatch).toHaveBeenCalledWith(
        AccountDetailActions.submitExchange({
          accountId: '1',
          req: { amount: 100, targetAccountId: '2' },
          idempotencyKey: expect.any(String),
        }),
      );
    });
  });

  describe('submitTransfer', () => {
    it('does nothing without username or account number', () => {
      component.openModal('transfer');
      component.modalAmount = 100;
      component.transferUsername = 'bob';
      // no account number
      component.submitTransfer();
      expect(store.dispatch).not.toHaveBeenCalledWith(expect.objectContaining({ type: expect.stringContaining('Submit Transfer') }));
    });

    it('dispatches submitTransfer with recipient details, omitting a blank description', () => {
      component.openModal('transfer');
      component.modalAmount = 50;
      component.transferUsername = 'bob';
      component.transferAccountNumber = 'ACC-002';

      component.submitTransfer();

      expect(store.dispatch).toHaveBeenCalledWith(
        AccountDetailActions.submitTransfer({
          accountId: '1',
          req: {
            amount: 50,
            targetUsername: 'bob',
            targetAccountNumber: 'ACC-002',
            description: undefined,
          },
          idempotencyKey: expect.any(String),
        }),
      );
    });
  });

  describe('hasSufficientFunds', () => {
    it('is true when the amount is at or below the balance', () => {
      component.modalAmount = 1000;
      expect(component.hasSufficientFunds()).toBe(true);
    });

    it('is false when the amount exceeds the balance', () => {
      component.modalAmount = 1000.01;
      expect(component.hasSufficientFunds()).toBe(false);
    });

    it('is false when no amount has been entered', () => {
      component.modalAmount = null;
      expect(component.hasSufficientFunds()).toBe(false);
    });
  });

  describe('recipient check (onRecipientFieldChange)', () => {
    it('stays idle and skips the API call while a field is empty', fakeAsync(() => {
      component.transferUsername = 'bob';
      component.transferAccountNumber = '';
      component.onRecipientFieldChange();
      tick(400);

      expect(bankService.checkRecipient).not.toHaveBeenCalled();
      expect(component.recipientStatus()).toBe('idle');
    }));

    it('debounces and reports a valid recipient', fakeAsync(() => {
      bankService.checkRecipient.mockReturnValue(of({ valid: true }));

      component.transferUsername = 'bob';
      component.transferAccountNumber = 'ACC-002';
      component.onRecipientFieldChange();

      expect(component.recipientStatus()).toBe('checking'); // not yet resolved
      tick(400);

      expect(bankService.checkRecipient).toHaveBeenCalledWith('bob', 'ACC-002');
      expect(component.recipientStatus()).toBe('valid');
    }));

    it('reports an invalid recipient', fakeAsync(() => {
      bankService.checkRecipient.mockReturnValue(of({ valid: false }));

      component.transferUsername = 'nobody';
      component.transferAccountNumber = 'BOGUS';
      component.onRecipientFieldChange();
      tick(400);

      expect(component.recipientStatus()).toBe('invalid');
    }));

    it('treats a failed check request as an invalid recipient', fakeAsync(() => {
      bankService.checkRecipient.mockReturnValue(throwError(() => new Error('network error')));

      component.transferUsername = 'bob';
      component.transferAccountNumber = 'ACC-002';
      component.onRecipientFieldChange();
      tick(400);

      expect(component.recipientStatus()).toBe('invalid');
    }));

    it('coalesces rapid edits into a single API call for the final values', fakeAsync(() => {
      bankService.checkRecipient.mockReturnValue(of({ valid: true }));

      component.transferUsername = 'b';
      component.onRecipientFieldChange();
      tick(100);
      component.transferUsername = 'bo';
      component.onRecipientFieldChange();
      tick(100);
      component.transferUsername = 'bob';
      component.transferAccountNumber = 'ACC-002';
      component.onRecipientFieldChange();
      tick(400);

      expect(bankService.checkRecipient).toHaveBeenCalledTimes(1);
      expect(bankService.checkRecipient).toHaveBeenCalledWith('bob', 'ACC-002');
    }));

    it('synchronously drops back to checking the instant a field changes, even before the new check resolves', fakeAsync(() => {
      bankService.checkRecipient.mockReturnValue(of({ valid: true }));
      component.transferUsername = 'bob';
      component.transferAccountNumber = 'ACC-002';
      component.onRecipientFieldChange();
      tick(400);
      expect(component.recipientStatus()).toBe('valid');

      component.transferAccountNumber = 'ACC-003';
      component.onRecipientFieldChange();

      expect(component.recipientStatus()).toBe('checking');
      tick(400);
    }));

    it('synchronously drops back to idle the instant a field is cleared, even before the new check resolves', fakeAsync(() => {
      bankService.checkRecipient.mockReturnValue(of({ valid: true }));
      component.transferUsername = 'bob';
      component.transferAccountNumber = 'ACC-002';
      component.onRecipientFieldChange();
      tick(400);
      expect(component.recipientStatus()).toBe('valid');

      component.transferAccountNumber = '';
      component.onRecipientFieldChange();

      expect(component.recipientStatus()).toBe('idle');
      tick(400);
    }));
  });

  describe('exchange helpers', () => {
    beforeEach(() => {
      store.overrideSelector(selectOtherAccounts, [
        { id: '2', accountNumber: 'ACC-002', currency: 'USD', balance: 500, userId: '10', username: 'alice' },
      ]);
      store.overrideSelector(selectExchangeRates, { EUR_USD: 1.1 });
      store.refreshState();
    });

    it('getTargetCurrency returns the currency of the selected target account', () => {
      component.targetAccountId = '2';
      expect(component.getTargetCurrency()).toBe('USD');
    });

    it('getTargetCurrency returns an empty string when nothing is selected', () => {
      component.targetAccountId = null;
      expect(component.getTargetCurrency()).toBe('');
    });

    it('getExchangeRate looks up the rate for the account currency pair', () => {
      component.targetAccountId = '2';
      expect(component.getExchangeRate()).toBe(1.1);
    });

    it('getConvertedAmount multiplies the entered amount by the exchange rate', () => {
      component.targetAccountId = '2';
      component.modalAmount = 100;
      expect(component.getConvertedAmount()).toBeCloseTo(110);
    });

    it('getConvertedAmount is 0 without an amount', () => {
      component.targetAccountId = '2';
      component.modalAmount = null;
      expect(component.getConvertedAmount()).toBe(0);
    });
  });

  describe('goToTransaction', () => {
    it('navigates to the transaction detail route', () => {
      component.goToTransaction('tx-42');
      expect(router.navigate).toHaveBeenCalledWith(['/transactions', 'tx-42']);
    });
  });

  describe('transaction display helpers', () => {
    it.each([
      ['CREDIT', 'badge-credit', 'Credit', 'amount-positive', '+'],
      ['DEBIT', 'badge-debit', 'Debit', 'amount-negative', '-'],
      ['EXCHANGE_IN', 'badge-credit', 'Exchange In', 'amount-positive', '+'],
      ['EXCHANGE_OUT', 'badge-debit', 'Exchange Out', 'amount-negative', '-'],
      ['TRANSFER_IN', 'badge-credit', 'Transfer In', 'amount-positive', '+'],
      ['TRANSFER_OUT', 'badge-debit', 'Transfer Out', 'amount-negative', '-'],
    ])('%s maps to badge %s, label %s, amount class %s, sign %s', (type, badge, label, amountClass, sign) => {
      expect(component.txBadgeClass(type)).toBe(badge);
      expect(component.txLabel(type)).toBe(label);
      expect(component.txAmountClass(type)).toBe(amountClass);
      expect(component.txSign(type)).toBe(sign);
    });

    it('falls back to the raw type string for an unknown label', () => {
      expect(component.txLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
    });
  });

  describe('formatting helpers', () => {
    it('formatAmount formats with two decimal places', () => {
      expect(component.formatAmount(1234.5)).toBe('1,234.50');
    });

    it('formatDate and formatChartDate do not throw on a valid ISO string', () => {
      expect(() => component.formatDate('2024-03-15T10:30:00Z')).not.toThrow();
      expect(() => component.formatChartDate('2024-03-15T10:30:00Z')).not.toThrow();
    });
  });

  describe('template: Send button gating', () => {
    beforeEach(() => {
      // activeModal must be set BEFORE the (only) detectChanges() call: a second explicit
      // detectChanges() after a plain-property mutation is unreliable in this test setup, while
      // the initial render always picks up whatever state the component was in beforehand.
      component.openModal('transfer');
      fixture.detectChanges();
    });

    function sendButton(): HTMLButtonElement {
      return fixture.nativeElement.querySelector('.modal-footer .btn-primary');
    }

    it('is disabled before any input is provided', () => {
      expect(sendButton().disabled).toBe(true);
    });

    it('enables once funds are sufficient and the recipient is verified', fakeAsync(() => {
      bankService.checkRecipient.mockReturnValue(of({ valid: true }));

      component.modalAmount = 100;
      component.transferUsername = 'bob';
      component.transferAccountNumber = 'ACC-002';
      component.onRecipientFieldChange();
      tick(400);
      fixture.detectChanges();

      expect(sendButton().disabled).toBe(false);
    }));

    it('stays disabled when funds are insufficient even with a verified recipient', fakeAsync(() => {
      bankService.checkRecipient.mockReturnValue(of({ valid: true }));

      component.modalAmount = 5000; // exceeds mockAccount.balance (1000)
      component.transferUsername = 'bob';
      component.transferAccountNumber = 'ACC-002';
      component.onRecipientFieldChange();
      tick(400);

      expect(sendButton().disabled).toBe(true);
    }));

    it('stays disabled when the recipient check has not resolved yet', fakeAsync(() => {
      bankService.checkRecipient.mockReturnValue(of({ valid: true }));

      component.modalAmount = 100;
      component.transferUsername = 'bob';
      component.transferAccountNumber = 'ACC-002';
      component.onRecipientFieldChange();
      // no tick(): debounce hasn't fired yet

      expect(sendButton().disabled).toBe(true);
      tick(400);
    }));
  });
});
