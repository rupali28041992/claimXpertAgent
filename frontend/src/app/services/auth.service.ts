import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface LoggedInUser {
  userId: string;
  name: string;
  email: string;
  customerId: string;
}

const STORAGE_KEY = 'claimxpert_user';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly base = environment.apiBaseUrl;

  private userSubject = new BehaviorSubject<LoggedInUser | null>(this.loadFromStorage());
  readonly user$ = this.userSubject.asObservable();

  constructor(private http: HttpClient) {}

  get currentUser(): LoggedInUser | null {
    return this.userSubject.value;
  }

  get isLoggedIn(): boolean {
    return !!this.userSubject.value;
  }

  login(userId: string, password: string): Observable<any> {
    return this.http.post<any>(`${this.base}/users/login`, { userId, password }).pipe(
      tap(res => {
        if (res.success) {
          const user: LoggedInUser = {
            userId: res.userId,
            name: res.name,
            email: res.email,
            customerId: res.customerId
          };
          localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
          this.userSubject.next(user);
        }
      })
    );
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.userSubject.next(null);
  }

  private loadFromStorage(): LoggedInUser | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }
}
