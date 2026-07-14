import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { jwtInterceptor } from './jwt.interceptor';
import { AuthService } from '../services/auth.service';

describe('jwtInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: jest.Mocked<Pick<AuthService, 'getToken' | 'clearSession'>>;
  let router: { navigate: jest.Mock };

  beforeEach(() => {
    authService = {
      getToken: jest.fn().mockReturnValue(null),
      clearSession: jest.fn(),
    };
    router = { navigate: jest.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── Token attachment ─────────────────────────────────────────────────────────

  it('attaches Authorization header when a token is stored', () => {
    authService.getToken.mockReturnValue('my-jwt');

    http.get('/api/accounts').subscribe();
    const req = httpMock.expectOne('/api/accounts');

    expect(req.request.headers.get('Authorization')).toBe('Bearer my-jwt');
    req.flush([]);
  });

  it('does not attach Authorization header when no token is stored', () => {
    http.get('/api/accounts').subscribe();
    const req = httpMock.expectOne('/api/accounts');

    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  // ── 401 handling ─────────────────────────────────────────────────────────────

  it('clears session and redirects to /login on 401 from a protected endpoint', () => {
    http.get('/api/accounts').subscribe({ error: () => {} });
    httpMock.expectOne('/api/accounts').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(authService.clearSession).toHaveBeenCalledTimes(1);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not redirect on 401 from /api/auth/ endpoints', () => {
    http.post('/api/auth/login', {}).subscribe({ error: () => {} });
    httpMock.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(authService.clearSession).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('does not redirect on non-401 errors', () => {
    http.get('/api/accounts').subscribe({ error: () => {} });
    httpMock.expectOne('/api/accounts').flush(null, { status: 500, statusText: 'Server Error' });

    expect(authService.clearSession).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('re-throws the error so callers can handle it', (done) => {
    http.get('/api/accounts').subscribe({
      error: (err) => {
        expect(err.status).toBe(403);
        done();
      },
    });
    httpMock.expectOne('/api/accounts').flush(null, { status: 403, statusText: 'Forbidden' });
  });
});
