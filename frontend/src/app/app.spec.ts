import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TOKEN_KEY } from './core/auth/auth.service';
import { makeToken } from './testing/make-token';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

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
  });

  it('shows the user email and a logout button when logged in', async () => {
    sessionStorage.setItem(
      TOKEN_KEY,
      makeToken({
        sub: 'u1',
        email: 'a@b.com',
        role: 'USER',
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('a@b.com');
    expect(el.textContent).toContain('Déconnexion');
    expect(el.querySelector('a[href="/login"]')).toBeNull();
  });
});
