export type Currency = "EUR" | "USD" | "CHF" | "GBP" | "SEK" | "VND";
export type TransactionType =
  | "CREDIT"
  | "DEBIT"
  | "EXCHANGE_IN"
  | "EXCHANGE_OUT";

export interface User {
  id: string;
  username: string;
  email: string;
  accounts?: Account[];
}

export interface Account {
  id: string;
  accountNumber: string;
  currency: Currency;
  balance: number;
  userId: string;
  username: string;
}

export interface AccountDetail extends Account {
  transactions: Transaction[];
}

export interface Transaction {
  id: string;
  accountId: string;
  accountNumber: string;
  type: TransactionType;
  amount: number;
  currency: Currency;
  balanceAfter: number;
  description: string;
  createdAt: string;
  exchangeRate?: number;
  relatedAccountId?: string;
  relatedAccountNumber?: string;
}

export interface MoneyRequest {
  amount: number;
  description?: string;
}

export interface ExchangeRequest {
  amount: number;
  targetAccountId: string;
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

export interface LoginChallengeResponse {
  challengeToken: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  username: string;
}

export interface AccountStats {
  totalIn: number;
  totalOut: number;
}
