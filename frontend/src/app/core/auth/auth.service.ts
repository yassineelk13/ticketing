import { HttpClient } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { API_BASE } from '../api.config';
import { LoginRequest, LoginResponse, RegisterRequest, UserResponse } from '../models/user.model';
import { decodeJwt, isExpired } from './jwt.util';

const TOKEN_KEY = 'billetterie.access-token';

@Service()
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenState = signal<string | null>(this.loadValidToken());

  readonly token = this.tokenState.asReadonly();
  readonly claims = computed(() => {
    const token = this.tokenState();
    return token ? decodeJwt(token) : null;
  });
  readonly isAuthenticated = computed(() => this.claims() !== null);
  readonly role = computed(() => this.claims()?.role ?? null);
  readonly userId = computed(() => this.claims()?.sub ?? null);
  readonly expiresAt = computed(() => {
    const claims = this.claims();
    return claims ? claims.exp * 1000 : null;
  });

  register(request: RegisterRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${API_BASE}/users/register`, request);
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${API_BASE}/users/login`, request)
      .pipe(tap((response) => this.setToken(response.accessToken)));
  }

  logout(): void {
    this.setToken(null);
  }

  private setToken(token: string | null): void {
    const valid = token !== null && decodeJwt(token) !== null ? token : null;
    this.tokenState.set(valid);
    try {
      if (valid) {
        sessionStorage.setItem(TOKEN_KEY, valid);
      } else {
        sessionStorage.removeItem(TOKEN_KEY);
      }
    } catch {
      // storage unavailable (private mode, quota): the session lives in memory only
    }
  }

  private loadValidToken(): string | null {
    try {
      const token = sessionStorage.getItem(TOKEN_KEY);
      const claims = token ? decodeJwt(token) : null;
      if (token && claims && !isExpired(claims)) {
        return token;
      }
      sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      // storage unavailable
    }
    return null;
  }
}
