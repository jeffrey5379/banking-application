import { Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { AccountsComponent } from './components/accounts/accounts.component';
import { LoginComponent } from './components/login/login.component';
import { AccountOverviewComponent } from './components/account-overview/account-overview.component';
import { TransactionOverviewComponent } from './components/transaction-overview/transaction-overview.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'accounts', component: AccountsComponent, canActivate: [authGuard] },
  { path: 'accounts/:id', component: AccountOverviewComponent, canActivate: [authGuard] },
  { path: 'transactions/:id', component: TransactionOverviewComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' }
];
