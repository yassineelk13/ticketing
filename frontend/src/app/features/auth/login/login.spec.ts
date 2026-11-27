import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { makeToken } from '../../../testing/make-token';
import { Login } from './login';

function typeInto(root: HTMLElement, name: string, value: string): void {
  const input = root.querySelector<HTMLInputElement>(`input[formControlName="${name}"]`);
  if (!input) {
    throw new Error(`Input ${name} not found`);
  }
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function submit(root: HTMLElement): void {
  root.querySelector('form')?.dispatchEvent(new Event('submit'));
}

describe('Login', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'login', component: Login },
          { path: 'events/:id', children: [] },
          { path: 'events', children: [] },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('does not call the API while the form is invalid', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login', Login);

    submit(harness.fixture.nativeElement);

    httpMock.expectNone('/api/users/login');
  });

  it('logs in and returns to the page the user came from', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login?returnUrl=%2Fevents%2F42', Login);
    const root: HTMLElement = harness.fixture.nativeElement;

    typeInto(root, 'email', 'a@b.com');
    typeInto(root, 'password', 'password123');
    submit(root);

    const req = httpMock.expectOne('/api/users/login');
    expect(req.request.body).toEqual({ email: 'a@b.com', password: 'password123' });
    req.flush({
      accessToken: makeToken({
        sub: 'u1',
        email: 'a@b.com',
        role: 'USER',
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
    });
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/events/42');
  });

  it('shows an inline error on bad credentials and stays on the page', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login', Login);
    const root: HTMLElement = harness.fixture.nativeElement;

    typeInto(root, 'email', 'a@b.com');
    typeInto(root, 'password', 'wrong-password');
    submit(root);
    httpMock.expectOne('/api/users/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    harness.detectChanges();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('incorrect');
    expect(TestBed.inject(Router).url).toBe('/login');
  });
});
