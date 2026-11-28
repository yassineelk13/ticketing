import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { App } from './app';
import { TOKEN_KEY } from './core/auth/auth.service';
import { NotificationService } from './core/notification/notification.service';
import { makeToken } from './testing/make-token';

describe('App', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  function storeTokenExpiringIn(seconds: number): void {
    sessionStorage.setItem(
      TOKEN_KEY,
      makeToken({
        sub: 'u1',
        email: 'a@b.com',
        role: 'USER',
        exp: Math.floor(Date.now() / 1000) + seconds,
      }),
    );
  }

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the brand and the events link', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.brand')?.textContent).toContain('Billetterie');
    expect(el.querySelector('a[href="/events"]')?.textContent).toContain('Événements');
  });

  it('offers login and registration when logged out', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('a[href="/login"]')).not.toBeNull();
    expect(el.querySelector('a[href="/register"]')).not.toBeNull();
    expect(el.textContent).not.toContain('Déconnexion');
    expect(el.querySelector('a[href="/tickets"]')).toBeNull();
    expect(el.querySelector('a[href="/profile"]')).toBeNull();
  });

  it('shows the user email and a logout button when logged in', async () => {
    storeTokenExpiringIn(3600);
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('a@b.com');
    expect(el.textContent).toContain('Déconnexion');
    expect(el.querySelector('a[href="/login"]')).toBeNull();
    expect(el.querySelector('a[href="/tickets"]')).not.toBeNull();
    expect(el.querySelector('a[href="/profile"]')).not.toBeNull();
  });

  it('notifies and redirects to login when the session expires', () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date('2026-01-01T00:00:00Z'));
      storeTokenExpiringIn(60);
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      const error = vi
        .spyOn(TestBed.inject(NotificationService), 'error')
        .mockImplementation(() => undefined);

      TestBed.createComponent(App);
      vi.advanceTimersByTime(61_000);

      expect(error).toHaveBeenCalledWith('Votre session a expiré. Veuillez vous reconnecter.');
      expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
    } finally {
      vi.useRealTimers();
    }
  });
});
