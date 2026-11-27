import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { NotificationService } from '../../../core/notification/notification.service';
import { Register } from './register';

function typeInto(root: HTMLElement, name: string, value: string): void {
  const input = root.querySelector<HTMLInputElement>(`input[formControlName="${name}"]`);
  if (!input) {
    throw new Error(`Input ${name} not found`);
  }
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function fillValidForm(root: HTMLElement): void {
  typeInto(root, 'firstName', 'Aya');
  typeInto(root, 'lastName', 'Benali');
  typeInto(root, 'email', 'aya@example.com');
  typeInto(root, 'password', 'password123');
}

function submit(root: HTMLElement): void {
  root.querySelector('form')?.dispatchEvent(new Event('submit'));
}

describe('Register', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'register', component: Register },
          { path: 'login', children: [] },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: NotificationService,
          useValue: { success: () => undefined, error: () => undefined },
        },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('sends the form values and goes to the login page on success', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/register', Register);
    const root: HTMLElement = harness.fixture.nativeElement;

    fillValidForm(root);
    submit(root);

    const req = httpMock.expectOne('/api/users/register');
    expect(req.request.body).toEqual({
      email: 'aya@example.com',
      password: 'password123',
      firstName: 'Aya',
      lastName: 'Benali',
      role: 'USER',
    });
    req.flush(
      { id: '1', email: 'aya@example.com', firstName: 'Aya', lastName: 'Benali', role: 'USER' },
      { status: 201, statusText: 'Created' },
    );
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('shows an inline message when the email is already used (409)', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/register', Register);
    const root: HTMLElement = harness.fixture.nativeElement;

    fillValidForm(root);
    submit(root);
    httpMock.expectOne('/api/users/register').flush(null, { status: 409, statusText: 'Conflict' });
    harness.detectChanges();
    harness.detectChanges();

    expect(root.textContent).toContain('déjà utilisée');
    expect(TestBed.inject(Router).url).toBe('/register');
  });
});
