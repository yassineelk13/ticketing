import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { makeToken } from '../../testing/make-token';
import { AuthService } from './auth.service';
import { vi } from 'vitest';

const KEY = 'billetterie.access-token';
const nowInSeconds = (): number => Math.floor(Date.now() / 1000);

describe('AuthService', () => {
  let httpMock: HttpTestingController;

  function setup(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => httpMock.verify());

  it('starts logged out', () => {
    const service = setup();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.role()).toBeNull();
  });

  it('stores the token and exposes its claims after login', () => {
    const service = setup();
    const token = makeToken({
      sub: 'u1',
      email: 'a@b.com',
      role: 'ORGANIZER',
      exp: nowInSeconds() + 3600,
    });

    service.login({ email: 'a@b.com', password: 'password123' }).subscribe();
    const req = httpMock.expectOne('/api/users/login');
    expect(req.request.method).toBe('POST');
    req.flush({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 3600 });

    expect(service.isAuthenticated()).toBe(true);
    expect(service.role()).toBe('ORGANIZER');
    expect(service.userId()).toBe('u1');
    expect(sessionStorage.getItem(KEY)).toBe(token);
  });

  it('clears the session on logout', () => {
    sessionStorage.setItem(
      KEY,
      makeToken({ sub: 'u1', email: 'a@b.com', role: 'USER', exp: nowInSeconds() + 3600 }),
    );
    const service = setup();
    expect(service.isAuthenticated()).toBe(true);

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(KEY)).toBeNull();
  });

  it('ignores and removes an expired stored token at startup', () => {
    sessionStorage.setItem(
      KEY,
      makeToken({ sub: 'u1', email: 'a@b.com', role: 'USER', exp: nowInSeconds() - 60 }),
    );
    const service = setup();
    expect(service.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(KEY)).toBeNull();
  });

  it('posts the registration to the API', () => {
    const service = setup();
    service
      .register({
        email: 'a@b.com',
        password: 'password123',
        firstName: 'A',
        lastName: 'B',
        role: 'USER',
      })
      .subscribe();
    const req = httpMock.expectOne('/api/users/register');
    expect(req.request.method).toBe('POST');
    req.flush({ id: '1', email: 'a@b.com', firstName: 'A', lastName: 'B', role: 'USER' });
  });
  describe('session expiry', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2026-01-01T00:00:00Z'));
    });

    afterEach(() => vi.useRealTimers());

    function storeTokenExpiringIn(seconds: number): void {
      sessionStorage.setItem(
        KEY,
        makeToken({
          sub: 'u1',
          email: 'a@b.com',
          role: 'USER',
          exp: Math.floor(Date.now() / 1000) + seconds,
        }),
      );
    }

    it('logs out and emits when the token expires', () => {
      storeTokenExpiringIn(60);
      const service = setup();
      let expiredEvents = 0;
      service.sessionExpired$.subscribe(() => expiredEvents++);

      vi.advanceTimersByTime(59_000);
      expect(service.isAuthenticated()).toBe(true);

      vi.advanceTimersByTime(2_000);
      expect(service.isAuthenticated()).toBe(false);
      expect(sessionStorage.getItem(KEY)).toBeNull();
      expect(expiredEvents).toBe(1);
    });

    it('does not emit after a manual logout', () => {
      storeTokenExpiringIn(60);
      const service = setup();
      let expiredEvents = 0;
      service.sessionExpired$.subscribe(() => expiredEvents++);

      service.logout();
      vi.advanceTimersByTime(120_000);

      expect(expiredEvents).toBe(0);
    });
  });
});
