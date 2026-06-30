export type Currency = 'EUR' | 'USD' | 'CHF' | 'GBP' | 'SEK' | 'VND';
export type TransactionType = 'CREDIT' | 'DEBIT' | 'EXCHANGE_IN' | 'EXCHANGE_OUT';

export interface User {
  id: number;
  username: string;
  email: string;
  accounts?: Account[];
}

export interface Account {
  id: number;
  accountNumber: string;
  currency: Currency;
  balance: number;
  userId: number;
  username: string;
}

export interface AccountDetail extends Account {
  transactions: Transaction[];
}

export interface Transaction {
  id: number;
  accountId: number;
  accountNumber: string;
  type: TransactionType;
  amount: number;
  currency: Currency;
  balanceAfter: number;
  description: string;
  createdAt: string;
  exchangeRate?: number;
  relatedAccountId?: number;
}

export interface MoneyRequest {
  amount: number;
  description?: string;
}

export interface ExchangeRequest {
  amount: number;
  targetAccountId: number;
}

export interface CreateAccountRequest {
  currency: Currency;
}

export interface BalancePoint {
  balance: number;
  createdAt: string;
}

export interface TransactionPage {
  content: Transaction[];
  page: number;
  size: number;
  totalElements: number;
  last: boolean;
}

export interface AuthResponse {
  token: string;
  userId: number;
  username: string;
}

export interface AccountStats {
  totalIn: number;
  totalOut: number;
}
