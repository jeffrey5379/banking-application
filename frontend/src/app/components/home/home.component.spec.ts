import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HomeComponent } from './home.component';
import { AuthService } from '../../services/auth.service';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let authService: jest.Mocked<Pick<AuthService, 'isLoggedIn' | 'isAdmin'>>;
  let router: { navigate: jest.Mock };

  beforeEach(() => {
    authService = { isLoggedIn: jest.fn(), isAdmin: jest.fn() };
    router = { navigate: jest.fn() };

    TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });

    fixture = TestBed.createComponent(HomeComponent);
  });

  it('redirects to /login when not logged in', () => {
    authService.isLoggedIn.mockReturnValue(false);

    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/login'], { replaceUrl: true });
  });

  it('redirects to /admin for a logged-in admin', () => {
    authService.isLoggedIn.mockReturnValue(true);
    authService.isAdmin.mockReturnValue(true);

    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/admin'], { replaceUrl: true });
  });

  it('redirects to /accounts for a logged-in non-admin', () => {
    authService.isLoggedIn.mockReturnValue(true);
    authService.isAdmin.mockReturnValue(false);

    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/accounts'], { replaceUrl: true });
  });
});
