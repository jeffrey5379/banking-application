import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  // ── HTTP ─────────────────────────────────────────────────────────────────────

  describe('login()', () => {
    it('POSTs to /api/auth/login with credentials', () => {
      service.login('alice', 'secret').subscribe();
      const req = httpMock.expectOne('/api/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ username: 'alice', password: 'secret' });
      req.flush({ token: 'tok123', userId: 1, username: 'alice' });
    });

    it('saves the token and user to localStorage after successful login', () => {
      service.login('alice', 'secret').subscribe();
      httpMock.expectOne('/api/auth/login').flush({ token: 'tok123', userId: 1, username: 'alice' });
      expect(service.getToken()).toBe('tok123');
      expect(service.getUser()).toEqual({ userId: 1, username: 'alice' });
    });
  });

  describe('register()', () => {
    it('POSTs to /api/auth/register with username, email, and password', () => {
      service.register('bob', 'bob@example.com', 'pass').subscribe();
      const req = httpMock.expectOne('/api/auth/register');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ username: 'bob', email: 'bob@example.com', password: 'pass' });
      req.flush({ token: 'tok456', userId: 2, username: 'bob' });
    });

    it('saves the token to localStorage after registration', () => {
      service.register('bob', 'bob@example.com', 'pass').subscribe();
      httpMock.expectOne('/api/auth/register').flush({ token: 'tok456', userId: 2, username: 'bob' });
      expect(service.isLoggedIn()).toBe(true);
      expect(service.getToken()).toBe('tok456');
    });
  });

  // ── Session state ─────────────────────────────────────────────────────────────

  describe('isLoggedIn()', () => {
    it('returns false when no token is stored', () => {
      expect(service.isLoggedIn()).toBe(false);
    });

    it('returns true after a successful login', () => {
      service.login('alice', 'pass').subscribe();
      httpMock.expectOne('/api/auth/login').flush({ token: 'tok', userId: 1, username: 'alice' });
      expect(service.isLoggedIn()).toBe(true);
    });
  });

  describe('getUser()', () => {
    it('returns null when not logged in', () => {
      expect(service.getUser()).toBeNull();
    });

    it('returns the parsed user object after login', () => {
      service.login('charlie', 'pw').subscribe();
      httpMock.expectOne('/api/auth/login').flush({ token: 'tok', userId: 3, username: 'charlie' });
      expect(service.getUser()).toEqual({ userId: 3, username: 'charlie' });
    });
  });

  describe('getToken()', () => {
    it('returns null when no token is stored', () => {
      expect(service.getToken()).toBeNull();
    });

    it('returns the stored token string', () => {
      localStorage.setItem('jwt_token', 'my-token');
      expect(service.getToken()).toBe('my-token');
    });
  });

  describe('clearSession()', () => {
    it('removes both token and user from localStorage', () => {
      localStorage.setItem('jwt_token', 'tok');
      localStorage.setItem('auth_user', JSON.stringify({ userId: 1, username: 'alice' }));

      service.clearSession();

      expect(service.getToken()).toBeNull();
      expect(service.getUser()).toBeNull();
    });

    it('is idempotent when called on an already-empty session', () => {
      expect(() => service.clearSession()).not.toThrow();
      expect(service.isLoggedIn()).toBe(false);
    });
  });

  describe('logout()', () => {
    it('POSTs to /api/auth/logout and clears localStorage on success', () => {
      localStorage.setItem('jwt_token', 'tok');
      localStorage.setItem('auth_user', JSON.stringify({ userId: 1, username: 'alice' }));

      service.logout().subscribe();
      httpMock.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });

      expect(service.isLoggedIn()).toBe(false);
      expect(service.getUser()).toBeNull();
    });

    it('clears localStorage even when the server call fails', () => {
      localStorage.setItem('jwt_token', 'tok');
      localStorage.setItem('auth_user', JSON.stringify({ userId: 1, username: 'alice' }));

      service.logout().subscribe({ error: () => {} });
      httpMock.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Error' });

      expect(service.isLoggedIn()).toBe(false);
      expect(service.getUser()).toBeNull();
    });
  });
});
