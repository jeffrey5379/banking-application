import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  User, Account, AccountStats, Transaction, TransactionPage, BalancePoint,
  MoneyRequest, ExchangeRequest, CreateAccountRequest
} from '../models/bank.models';

@Injectable({ providedIn: 'root' })
export class BankService {
  private readonly api = '/api';

  constructor(private http: HttpClient) {}

  // ── Users ─────────────────────────────────────────────────────────
  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${this.api}/users`);
  }

  getUser(id: number): Observable<User> {
    return this.http.get<User>(`${this.api}/users/${id}`);
  }

  createUser(username: string, email: string): Observable<User> {
    return this.http.post<User>(`${this.api}/users`, { username, email });
  }

  // ── Accounts ──────────────────────────────────────────────────────
  getAccountsByUser(userId: number): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.api}/accounts/user/${userId}`);
  }

  getAccountSummary(accountId: number): Observable<Account> {
    return this.http.get<Account>(`${this.api}/accounts/${accountId}`);
  }

  createAccount(req: CreateAccountRequest): Observable<Account> {
    return this.http.post<Account>(`${this.api}/accounts`, req);
  }

  credit(accountId: number, req: MoneyRequest): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.api}/accounts/${accountId}/credit`, req);
  }

  debit(accountId: number, req: MoneyRequest): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.api}/accounts/${accountId}/debit`, req);
  }

  exchange(accountId: number, req: ExchangeRequest): Observable<Transaction[]> {
    return this.http.post<Transaction[]>(`${this.api}/accounts/${accountId}/exchange`, req);
  }

  // ── Transactions ──────────────────────────────────────────────────
  getBalanceHistory(accountId: number): Observable<BalancePoint[]> {
    return this.http.get<BalancePoint[]>(`${this.api}/accounts/${accountId}/balance-history`);
  }

  getTransactionsPaged(accountId: number, page: number, size: number): Observable<TransactionPage> {
    return this.http.get<TransactionPage>(
      `${this.api}/accounts/${accountId}/transactions?page=${page}&size=${size}`
    );
  }

  getTransaction(transactionId: number): Observable<Transaction> {
    return this.http.get<Transaction>(`${this.api}/accounts/transactions/${transactionId}`);
  }

  getAccountStats(accountId: number): Observable<AccountStats> {
    return this.http.get<AccountStats>(`${this.api}/accounts/${accountId}/summary`);
  }

  // ── Exchange Rates ────────────────────────────────────────────────
  getExchangeRates(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.api}/exchange-rates`);
  }
}
