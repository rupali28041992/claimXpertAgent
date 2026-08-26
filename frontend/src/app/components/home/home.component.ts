import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent {
  constructor(public authService: AuthService) {}

  get greeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return '☀️ Good morning';
    if (hour < 17) return '👋 Good afternoon';
    return '🌙 Good evening';
  }

  get firstName(): string {
    return this.authService.currentUser?.name?.split(' ')[0] ?? 'there';
  }
}
