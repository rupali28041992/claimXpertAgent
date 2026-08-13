import { Routes } from '@angular/router';
import { ChatPortalComponent } from './components/chat-portal/chat-portal.component';
import { AddPolicyComponent } from './components/add-policy/add-policy.component';

export const routes: Routes = [
  { path: '', component: ChatPortalComponent },
  { path: 'add-policy', component: AddPolicyComponent }
];
