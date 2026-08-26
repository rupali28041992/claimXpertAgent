import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { HomeComponent } from './components/home/home.component';
import { ChatPortalComponent } from './components/chat-portal/chat-portal.component';
import { InvestigationProgressComponent } from './components/investigation-progress/investigation-progress.component';
import { FinalDecisionComponent } from './components/final-decision/final-decision.component';
import { ClaimStatusComponent } from './components/claim-status/claim-status.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login',                    component: LoginComponent },
  { path: 'home',                     component: HomeComponent,                    canActivate: [authGuard] },
  { path: 'file-claim',               component: ChatPortalComponent,              canActivate: [authGuard] },
  { path: 'investigating/:claimId',   component: InvestigationProgressComponent,   canActivate: [authGuard] },
  { path: 'decision/:claimId',        component: FinalDecisionComponent,           canActivate: [authGuard] },
  { path: 'status',                   component: ClaimStatusComponent,             canActivate: [authGuard] },
  { path: '',                         redirectTo: '/home',  pathMatch: 'full' },
  { path: '**',                       redirectTo: '/home' }
];
