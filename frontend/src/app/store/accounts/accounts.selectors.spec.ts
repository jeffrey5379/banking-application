import { selectCurrencyTotals } from './accounts.selectors';
import { Account } from '../../models/bank.models';

const acct = (id: number, currency: Account['currency'], balance: number): Account => ({
  id,
  accountNumber: `ACC-00${id}`,
  currency,
  balance,
  userId: 1,
  username: 'alice',
});

describe('selectCurrencyTotals', () => {
  it('returns empty array when there are no accounts', () => {
    expect(selectCurrencyTotals.projector([])).toEqual([]);
  });

  it('groups accounts by currency and sums balances', () => {
    const accounts = [
      acct(1, 'EUR', 500),
      acct(2, 'EUR', 300),
      acct(3, 'USD', 1000),
    ];
    const result = selectCurrencyTotals.projector(accounts);
    const eur = result.find((r) => r.currency === 'EUR');
    const usd = result.find((r) => r.currency === 'USD');
    expect(result).toHaveLength(2);
    expect(eur?.total).toBe(800);
    expect(usd?.total).toBe(1000);
  });

  it('handles a single account', () => {
    const result = selectCurrencyTotals.projector([acct(1, 'GBP', 250)]);
    expect(result).toEqual([{ currency: 'GBP', total: 250 }]);
  });

  it('handles all four supported currencies', () => {
    const accounts = [
      acct(1, 'EUR', 100),
      acct(2, 'USD', 200),
      acct(3, 'GBP', 300),
      acct(4, 'CHF', 400),
    ];
    const result = selectCurrencyTotals.projector(accounts);
    expect(result).toHaveLength(4);
    expect(result.find((r) => r.currency === 'CHF')?.total).toBe(400);
  });
});
