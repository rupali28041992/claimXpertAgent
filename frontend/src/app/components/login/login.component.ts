import { Component, ChangeDetectorRef } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  form!: FormGroup;
  isLoading = false;
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {
    this.form = this.fb.group({
      userId: ['', [Validators.required]],
      password: ['', [Validators.required]]
    });
  }

  onSubmit(): void {
    if (this.form.invalid || this.isLoading) return;
    this.isLoading = true;
    this.errorMessage = '';

    const { userId, password } = this.form.value;
    this.authService.login(userId.trim(), password).subscribe({
      next: res => {
        if (res.success) {
          this.router.navigate(['/home']);
        } else {
          this.errorMessage = res.message || 'Invalid credentials. Please try again.';
          this.isLoading = false;
          this.cdr.detectChanges();
        }
      },
      error: err => {
        this.errorMessage = err.error?.message || 'Invalid user ID or password. Please try again.';
        this.isLoading = false;
        this.cdr.detectChanges();
      }
    });
  }
}
