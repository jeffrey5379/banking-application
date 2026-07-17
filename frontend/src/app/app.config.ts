import { ApplicationConfig, isDevMode } from '@angular/core';
import { provideRouter } from '@angular/router';
import { LocationStrategy } from '@angular/common';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';
import { routes } from './app.routes';
import { jwtInterceptor } from './interceptors/jwt.interceptor';
import { NoopLocationStrategy } from './routing/no-op-location-strategy';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { accountsFeature } from './store/accounts/accounts.reducer';
import { AccountsEffects } from './store/accounts/accounts.effects';
import { accountDetailFeature } from './store/account-detail/account-detail.reducer';
import { AccountDetailEffects } from './store/account-detail/account-detail.effects';
import { transactionFeature } from './store/transaction/transaction.reducer';
import { TransactionEffects } from './store/transaction/transaction.effects';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    { provide: LocationStrategy, useClass: NoopLocationStrategy },
    provideHttpClient(withXhr(), withInterceptors([jwtInterceptor])),
    provideCharts(withDefaultRegisterables()),
    provideStore({
      [accountsFeature.name]: accountsFeature.reducer,
      [accountDetailFeature.name]: accountDetailFeature.reducer,
      [transactionFeature.name]: transactionFeature.reducer,
    }),
    provideEffects(AccountsEffects, AccountDetailEffects, TransactionEffects),
    provideStoreDevtools({ maxAge: 25, logOnly: !isDevMode() }),
  ],
};
