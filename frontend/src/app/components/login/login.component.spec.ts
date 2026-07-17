import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../services/auth.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: jest.Mocked<Pick<AuthService, 'login' | 'verifyOtp' | 'register'>>;
  let router: { navigate: jest.Mock };

  beforeEach(() => {
    authService = { login: jest.fn(), verifyOtp: jest.fn(), register: jest.fn() };
    router = { navigate: jest.fn() };

    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  });

  it('starts on the login credentials step', () => {
    expect(component.mode).toBe('login');
    expect(component.step).toBe('credentials');
  });

  describe('switchMode()', () => {
    it('resets step, error, password, and otpCode when switching tabs', () => {
      component.step = 'otp';
      component.error = 'some error';
      component.password = 'secret';
      component.otpCode = '123456';

      component.switchMode('register');

      expect(component.mode).toBe('register');
      expect(component.step).toBe('credentials');
      expect(component.error).toBe('');
      expect(component.password).toBe('');
      expect(component.otpCode).toBe('');
    });
  });

  describe('onLogin()', () => {
    it('shows a validation error and does not call the service when fields are empty', () => {
      component.username = '';
      component.password = '';

      component.onLogin();

      expect(component.error).toBe('Please fill in all fields.');
      expect(authService.login).not.toHaveBeenCalled();
    });

    it('advances to the OTP step and stores the challenge token on success', () => {
      component.username = 'alice';
      component.password = 'secret';
      authService.login.mockReturnValue(of({ challengeToken: 'challenge-token-1' }));

      component.onLogin();

      expect(authService.login).toHaveBeenCalledWith('alice', 'secret');
      expect(component.step).toBe('otp');
      expect(component.loading).toBe(false);
    });

    it('shows an invalid-credentials message on a 401', () => {
      component.username = 'alice';
      component.password = 'wrong';
      authService.login.mockReturnValue(throwError(() => ({ status: 401 })));

      component.onLogin();

      expect(component.error).toBe('Invalid username or password.');
      expect(component.step).toBe('credentials');
      expect(component.loading).toBe(false);
    });

    it('shows a generic message on other failures', () => {
      component.username = 'alice';
      component.password = 'secret';
      authService.login.mockReturnValue(throwError(() => ({ status: 500 })));

      component.onLogin();

      expect(component.error).toBe('Login failed. Please try again.');
    });
  });

  describe('onVerifyOtp()', () => {
    beforeEach(() => {
      // Reach the OTP step with a known challenge token, the way a real user would.
      component.username = 'alice';
      component.password = 'secret';
      authService.login.mockReturnValue(of({ challengeToken: 'challenge-token-1' }));
      component.onLogin();
    });

    it('shows a validation error and does not call the service when the code is empty', () => {
      component.otpCode = '';

      component.onVerifyOtp();

      expect(component.error).toBe('Please enter the code.');
      expect(authService.verifyOtp).not.toHaveBeenCalled();
    });

    it('verifies with the stored challenge token and navigates to /accounts on success', () => {
      component.otpCode = '111111';
      authService.verifyOtp.mockReturnValue(of({ token: 'jwt', userId: '1', username: 'alice' }));

      component.onVerifyOtp();

      expect(authService.verifyOtp).toHaveBeenCalledWith('challenge-token-1', '111111');
      expect(router.navigate).toHaveBeenCalledWith(['/accounts']);
    });

    it('shows the backend error message when the code is rejected', () => {
      component.otpCode = '000000';
      authService.verifyOtp.mockReturnValue(
        throwError(() => ({ error: { message: 'Invalid code' } })),
      );

      component.onVerifyOtp();

      expect(component.error).toBe('Invalid code');
      expect(component.loading).toBe(false);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('falls back to a generic message when the backend gives no message', () => {
      component.otpCode = '000000';
      authService.verifyOtp.mockReturnValue(throwError(() => ({})));

      component.onVerifyOtp();

      expect(component.error).toBe('Invalid or expired code.');
    });
  });

  describe('backToCredentials()', () => {
    it('returns to the credentials step and clears the code and error', () => {
      component.step = 'otp';
      component.otpCode = '111111';
      component.error = 'Invalid code';

      component.backToCredentials();

      expect(component.step).toBe('credentials');
      expect(component.otpCode).toBe('');
      expect(component.error).toBe('');
    });
  });

  describe('onRegister()', () => {
    it('does nothing when the form is invalid', () => {
      component.onRegister({ invalid: true });

      expect(authService.register).not.toHaveBeenCalled();
    });

    it('registers and navigates to /accounts on success', () => {
      component.username = 'bob';
      component.email = 'bob@example.com';
      component.password = 'pass1234';
      authService.register.mockReturnValue(of({ token: 'jwt', userId: '2', username: 'bob' }));

      component.onRegister({ invalid: false });

      expect(authService.register).toHaveBeenCalledWith('bob', 'bob@example.com', 'pass1234');
      expect(router.navigate).toHaveBeenCalledWith(['/accounts']);
    });

    it('shows the backend error message on failure', () => {
      authService.register.mockReturnValue(
        throwError(() => ({ error: { message: 'Username already exists' } })),
      );

      component.onRegister({ invalid: false });

      expect(component.error).toBe('Username already exists');
      expect(component.loading).toBe(false);
    });
  });

  describe('template', () => {
    it('shows the credentials form and hides the OTP form on the credentials step', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;

      expect(compiled.querySelector('input[name="password"]')).toBeTruthy();
      expect(compiled.querySelector('input[name="otpCode"]')).toBeFalsy();
    });

    it('shows the OTP form once the credentials step succeeds', () => {
      authService.login.mockReturnValue(of({ challengeToken: 'challenge-token-1' }));
      component.username = 'alice';
      component.password = 'secret';
      component.onLogin();
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('input[name="otpCode"]')).toBeTruthy();
      expect(compiled.querySelector('input[name="password"]')).toBeFalsy();
    });
  });
});
