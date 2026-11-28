import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UserResponse } from '../../../core/models/user.model';
import { NotificationService } from '../../../core/notification/notification.service';
import { Profile } from './profile';

const aya: UserResponse = {
  id: 'u1',
  email: 'aya@example.com',
  firstName: 'Aya',
  lastName: 'Benali',
  role: 'USER',
};

const ME = { method: 'GET', url: '/api/users/me' };
const UPDATE = { method: 'PUT', url: '/api/users/me' };

describe('Profile', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<Profile>;
  let successes: string[];
  let errors: string[];

  const root = (): HTMLElement => fixture.nativeElement;
  const text = (): string => root().textContent ?? '';
  const input = (name: string): HTMLInputElement => {
    const element = root().querySelector<HTMLInputElement>(`input[formControlName="${name}"]`);
    if (!element) {
      throw new Error(`Input ${name} not found`);
    }
    return element;
  };
  const saveButton = (): HTMLButtonElement => {
    const element = root().querySelector<HTMLButtonElement>('button.save');
    if (!element) {
      throw new Error('Save button not found');
    }
    return element;
  };

  function type(name: string, value: string): void {
    const element = input(name);
    element.value = value;
    element.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit(): void {
    root().querySelector('form')?.dispatchEvent(new Event('submit'));
  }

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function showProfile(): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne(ME).flush(aya);
    await settle();
  }

  beforeEach(() => {
    successes = [];
    errors = [];
    TestBed.configureTestingModule({
      imports: [Profile],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: NotificationService,
          useValue: {
            success: (message: string) => successes.push(message),
            error: (message: string) => errors.push(message),
          },
        },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Profile);
  });

  afterEach(() => httpMock.verify());

  it('shows the profile, prefills the form and keeps save disabled until something changes', async () => {
    fixture.detectChanges();
    expect(text()).toContain('Chargement');
    httpMock.expectOne(ME).flush(aya);
    await settle();

    expect(text()).toContain('aya@example.com');
    expect(text()).toContain('Participant');
    expect(input('firstName').value).toBe('Aya');
    expect(input('lastName').value).toBe('Benali');
    expect(saveButton().disabled).toBe(true);

    type('firstName', 'Aya B');
    expect(saveButton().disabled).toBe(false);
  });

  it('saves trimmed values, confirms, and becomes pristine again', async () => {
    await showProfile();

    type('firstName', '  Sara ');
    type('lastName', 'Alaoui');
    submit();

    const req = httpMock.expectOne(UPDATE);
    expect(req.request.body).toEqual({ firstName: 'Sara', lastName: 'Alaoui' });
    req.flush({ ...aya, firstName: 'Sara', lastName: 'Alaoui' });
    await settle();

    expect(successes).toEqual(['Profil mis à jour.']);
    expect(input('firstName').value).toBe('Sara');
    expect(saveButton().disabled).toBe(true);
  });

  it('does not send a name made only of spaces', async () => {
    await showProfile();

    type('firstName', '   ');
    submit();
    httpMock.expectNone(UPDATE);
    await settle();

    expect(text()).toContain('Ce champ est obligatoire.');
  });

  it('shows the backend validation errors under the matching field', async () => {
    await showProfile();

    type('firstName', 'Sara');
    submit();
    httpMock
      .expectOne(UPDATE)
      .flush(
        { errors: { firstName: 'size must be between 1 and 50' } },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();

    expect(text()).toContain('size must be between 1 and 50');
    expect(errors).toEqual([]);
  });

  it('shows an error state and recovers with the retry button', async () => {
    fixture.detectChanges();
    httpMock.expectOne(ME).flush(null, { status: 503, statusText: 'Service Unavailable' });
    await settle();
    expect(root().querySelector('[role="alert"]')?.textContent).toContain('Impossible');

    root().querySelector<HTMLButtonElement>('button.retry')?.click();
    httpMock.expectOne(ME).flush(aya);
    await settle();

    expect(text()).toContain('aya@example.com');
  });
});
