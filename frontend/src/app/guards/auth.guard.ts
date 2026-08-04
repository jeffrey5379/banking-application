import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

// Also fences admin users out of every ordinary authenticated page
// /admin is the only page an admin ever sees
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (!authService.isLoggedIn()) return router.createUrlTree(['/login']);
  if (authService.isAdmin()) return router.createUrlTree(['/admin']);
  return true;
};
