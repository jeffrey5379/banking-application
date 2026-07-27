import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Scoped to our own API - a presigned S3/MinIO upload URL is a different origin entirely, and
  // attaching our JWT to it would be at best useless and at worst trigger an unwanted CORS
  // preflight on a URL whose auth is already fully carried by its signature.
  const token = authService.getToken();
  if (token && req.url.startsWith('/api/')) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !req.url.includes('/api/auth/')) {
        authService.clearSession();
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
