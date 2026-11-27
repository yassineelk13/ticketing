import { HttpClient, HttpContext } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';
import { Observable, Subject, tap } from 'rxjs';
import { API_BASE } from '../api.config';
import { SILENT_ERRORS } from '../http/api-context';
import { LoginRequest, LoginResponse, RegisterRequest, UserResponse } from '../models/user.model';
import { decodeJwt, isExpired } from './jwt.util';

export const TOKEN_KEY = 'billetterie.access-token';
// setTimeout cannot wait longer than 2^31 - 1 ms (about 24.8 days)
const MAX_TIMEOUT_MS = 2_147_483_647;

@Service()
export class AuthService {
  private readonly http = inject(HttpClient);
  // Login and registration errors are shown inline by the forms themselves.
  private readonly silentErrors = { context: new HttpContext().set(SILENT_ERRORS, true) };
  private readonly tokenState = signal<string | null>(this.loadValidToken());
  private readonly expired = new Subject<void>();
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;

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

  /** Emits when the session ends because the token expired (not on a manual logout). */
  readonly sessionExpired$ = this.expired.asObservable();

  constructor() {
    this.scheduleExpiry();
  }

  register(request: RegisterRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${API_BASE}/users/register`, request, this.silentErrors);
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${API_BASE}/users/login`, request, this.silentErrors)
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
    this.scheduleExpiry();
  }

  private scheduleExpiry(): void {
    clearTimeout(this.expiryTimer);
    this.expiryTimer = undefined;
    const expiresAt = this.expiresAt();
    if (expiresAt === null) {
      return;
    }
    const delay = Math.min(Math.max(expiresAt - Date.now(), 0), MAX_TIMEOUT_MS);
    this.expiryTimer = setTimeout(() => {
      // A sleeping computer can fire timers late, and a very long delay was capped: check again.
      if (Date.now() >= expiresAt) {
        this.setToken(null);
        this.expired.next();
      } else {
        this.scheduleExpiry();
      }
    }, delay);
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
