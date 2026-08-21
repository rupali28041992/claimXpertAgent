import { Routes } from '@angular/router';
import { ChatPortalComponent } from './components/chat-portal/chat-portal.component';
import { InvestigationProgressComponent } from './components/investigation-progress/investigation-progress.component';
import { FinalDecisionComponent } from './components/final-decision/final-decision.component';

export const routes: Routes = [
  { path: '',                       component: ChatPortalComponent },
  { path: 'investigating/:claimId', component: InvestigationProgressComponent },
  { path: 'decision/:claimId',      component: FinalDecisionComponent },
  { path: '**',                     redirectTo: '' }
];
