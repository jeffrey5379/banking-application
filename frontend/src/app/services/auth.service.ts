import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, finalize, tap } from 'rxjs';
import { AuthResponse } from '../models/bank.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'jwt_token';
  private readonly USER_KEY = 'auth_user';

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { username, password }).pipe(
      tap(res => this.saveSession(res))
    );
  }

  register(username: string, email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', { username, email, password }).pipe(
      tap(res => this.saveSession(res))
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}).pipe(
      finalize(() => this.clearSession())
    );
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getUser(): { userId: number; username: string } | null {
    const raw = localStorage.getItem(this.USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }

  private saveSession(res: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, res.token);
    localStorage.setItem(this.USER_KEY, JSON.stringify({ userId: res.userId, username: res.username }));
  }

  clearSession(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }
}
