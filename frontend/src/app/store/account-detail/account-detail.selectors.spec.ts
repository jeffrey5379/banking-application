import { selectOtherAccounts } from './account-detail.selectors';
import { Account, AccountDetail } from '../../models/bank.models';

const acct = (id: number, currency: Account['currency']): Account => ({
  id: String(id),
  accountNumber: `ACC-00${id}`,
  currency,
  balance: 100,
  userId: '1',
  username: 'alice',
});

const detail = (id: number, currency: Account['currency']): AccountDetail => ({
  ...acct(id, currency),
  transactions: [],
});

describe('selectOtherAccounts', () => {
  it('excludes the currently viewed account by id', () => {
    const account = detail(1, 'EUR');
    const allAccounts = [acct(1, 'EUR'), acct(2, 'USD'), acct(3, 'GBP')];
    const result = selectOtherAccounts.projector(account, allAccounts);
    expect(result.every((a) => a.id !== '1')).toBe(true);
  });

  it('excludes accounts with the same currency (exchange must cross currencies)', () => {
    const account = detail(1, 'EUR');
    const allAccounts = [acct(2, 'EUR'), acct(3, 'USD')];
    const result = selectOtherAccounts.projector(account, allAccounts);
    expect(result.every((a) => a.currency !== 'EUR')).toBe(true);
  });

  it('returns only valid exchange targets', () => {
    const account = detail(1, 'EUR');
    const allAccounts = [
      acct(1, 'EUR'), // same id → excluded
      acct(2, 'EUR'), // same currency → excluded
      acct(3, 'USD'), // valid
      acct(4, 'GBP'), // valid
    ];
    const result = selectOtherAccounts.projector(account, allAccounts);
    expect(result.map((a) => a.id)).toEqual(['3', '4']);
  });

  it('returns empty array when the user has no other-currency accounts', () => {
    const account = detail(1, 'EUR');
    const allAccounts = [acct(1, 'EUR'), acct(2, 'EUR')];
    const result = selectOtherAccounts.projector(account, allAccounts);
    expect(result).toHaveLength(0);
  });
});
