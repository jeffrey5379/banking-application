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

  getUser(id: string): Observable<User> {
    return this.http.get<User>(`${this.api}/users/${id}`);
  }

  createUser(username: string, email: string): Observable<User> {
    return this.http.post<User>(`${this.api}/users`, { username, email });
  }

  // ── Accounts ──────────────────────────────────────────────────────
  getAccountsByUser(userId: string): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.api}/accounts/user/${userId}`);
  }

  getAccountSummary(accountId: string): Observable<Account> {
    return this.http.get<Account>(`${this.api}/accounts/${accountId}`);
  }

  createAccount(req: CreateAccountRequest, idempotencyKey: string): Observable<Account> {
    return this.http.post<Account>(`${this.api}/accounts`, req, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  credit(accountId: string, req: MoneyRequest, idempotencyKey: string): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.api}/accounts/${accountId}/credit`, req, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  debit(accountId: string, req: MoneyRequest, idempotencyKey: string): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.api}/accounts/${accountId}/debit`, req, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  exchange(accountId: string, req: ExchangeRequest, idempotencyKey: string): Observable<Transaction[]> {
    return this.http.post<Transaction[]>(`${this.api}/accounts/${accountId}/exchange`, req, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  // ── Transactions ──────────────────────────────────────────────────
  getBalanceHistory(accountId: string): Observable<BalancePoint[]> {
    return this.http.get<BalancePoint[]>(`${this.api}/accounts/${accountId}/balance-history`);
  }

  getTransactionsPaged(accountId: string, page: number, size: number): Observable<TransactionPage> {
    return this.http.get<TransactionPage>(
      `${this.api}/accounts/${accountId}/transactions?page=${page}&size=${size}`
    );
  }

  getTransaction(transactionId: string): Observable<Transaction> {
    return this.http.get<Transaction>(`${this.api}/accounts/transactions/${transactionId}`);
  }

  getAccountStats(accountId: string): Observable<AccountStats> {
    return this.http.get<AccountStats>(`${this.api}/accounts/${accountId}/summary`);
  }

  // ── Exchange Rates ────────────────────────────────────────────────
  getExchangeRates(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.api}/exchange-rates`);
  }
}
