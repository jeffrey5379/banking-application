import { Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { AccountsComponent } from './components/accounts/accounts.component';
import { LoginComponent } from './components/login/login.component';
import { AccountOverviewComponent } from './components/account-overview/account-overview.component';
import { TransactionOverviewComponent } from './components/transaction-overview/transaction-overview.component';
import { KycComponent } from './components/kyc/kyc.component';
import { MessagesComponent } from './components/messages/messages.component';
import { AdminComponent } from './components/admin/admin.component';
import { authGuard } from './guards/auth.guard';
import { adminGuard } from './guards/admin.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'accounts', component: AccountsComponent, canActivate: [authGuard] },
  { path: 'accounts/:id', component: AccountOverviewComponent, canActivate: [authGuard] },
  { path: 'transactions/:id', component: TransactionOverviewComponent, canActivate: [authGuard] },
  { path: 'kyc', component: KycComponent, canActivate: [authGuard] },
  { path: 'messages', component: MessagesComponent, canActivate: [authGuard] },
  { path: 'admin', component: AdminComponent, canActivate: [adminGuard] },
  { path: '**', redirectTo: '' }
];
