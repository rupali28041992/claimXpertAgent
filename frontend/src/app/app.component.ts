import { Component } from '@angular/core';
import { ChatPortalComponent } from './components/chat-portal/chat-portal.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ChatPortalComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {}
