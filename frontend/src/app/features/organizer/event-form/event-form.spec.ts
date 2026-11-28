import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TOKEN_KEY } from '../../../core/auth/auth.service';
import { EventResponse } from '../../../core/models/event.model';
import { NotificationService } from '../../../core/notification/notification.service';
import { makeToken } from '../../../testing/make-token';
import { EventForm } from './event-form';

const FUTURE_LOCAL = '2099-01-15T20:00';
const FUTURE_ISO = new Date(FUTURE_LOCAL).toISOString();

const mine: EventResponse = {
  id: 'e1',
  title: 'Concert Gnawa',
  description: 'Soirée musicale',
  type: 'CONCERT',
  city: 'Agadir',
  venue: 'Theatre de Verdure',
  startDate: FUTURE_ISO,
  totalSeats: 200,
  organizerId: 'o1',
  attributes: { artist: 'Maalem Hamid', ageLimit: 18 },
};

describe('EventForm', () => {
  let httpMock: HttpTestingController;
  let harness: RouterTestingHarness;
  let successes: string[];
  let errors: string[];

  const root = (): HTMLElement => harness.fixture.nativeElement;
  const text = (): string => root().textContent ?? '';

  const field = (name: string): HTMLInputElement => {
    const element = root().querySelector<HTMLInputElement>(`[formControlName="${name}"]`);
    if (!element) {
      throw new Error(`Field ${name} not found`);
    }
    return element;
  };

  function typeInto(name: string, value: string): void {
    const element = field(name);
    element.value = value;
    element.dispatchEvent(new Event('input'));
  }

  function fillValidForm(): void {
    typeInto('title', 'Concert Gnawa');
    typeInto('city', 'Agadir');
    typeInto('venue', 'Theatre de Verdure');
    typeInto('startDate', FUTURE_LOCAL);
    typeInto('totalSeats', '50');
  }

  function submit(): void {
    root().querySelector('form')?.dispatchEvent(new Event('submit'));
  }

  async function open(url: string): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, EventForm);
    harness.detectChanges();
    harness.detectChanges();
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem(
      TOKEN_KEY,
      makeToken({
        sub: 'o1',
        email: 'org@example.com',
        role: 'ORGANIZER',
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    successes = [];
    errors = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'organizer/events/new', component: EventForm },
            { path: 'organizer/events/:id/edit', component: EventForm },
            { path: 'organizer/events', children: [] },
          ],
          withComponentInputBinding(),
        ),
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
  });

  afterEach(() => httpMock.verify());

  it('creates an event with the date converted to UTC, then returns to my events', async () => {
    await open('/organizer/events/new');
    fillValidForm();
    root().querySelector<HTMLButtonElement>('button.add-attribute')?.click();
    harness.detectChanges();
    typeInto('key', 'artist');
    typeInto('value', 'Stormy');

    submit();

    const req = httpMock.expectOne({ method: 'POST', url: '/api/events' });
    expect(req.request.body).toEqual({
      title: 'Concert Gnawa',
      type: 'CONCERT',
      city: 'Agadir',
      venue: 'Theatre de Verdure',
      startDate: FUTURE_ISO,
      totalSeats: 50,
      attributes: { artist: 'Stormy' },
    });
    req.flush({ ...mine, id: 'new' }, { status: 201, statusText: 'Created' });
    await settle();

    expect(successes).toEqual(['Événement créé.']);
    expect(TestBed.inject(Router).url).toBe('/organizer/events');
  });

  it('blocks a past date before calling the API', async () => {
    await open('/organizer/events/new');
    fillValidForm();
    typeInto('startDate', '2020-01-01T10:00');

    submit();
    await settle();

    httpMock.expectNone({ method: 'POST', url: '/api/events' });
    expect(text()).toContain('dans le futur');
  });

  it('shows backend validation errors under the matching field', async () => {
    await open('/organizer/events/new');
    fillValidForm();

    submit();
    httpMock
      .expectOne({ method: 'POST', url: '/api/events' })
      .flush(
        { errors: { title: 'size must be between 1 and 150' } },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();

    expect(text()).toContain('size must be between 1 and 150');
    expect(errors).toEqual([]);
  });

  it('explains a 403 when creating', async () => {
    await open('/organizer/events/new');
    fillValidForm();

    submit();
    httpMock
      .expectOne({ method: 'POST', url: '/api/events' })
      .flush(null, { status: 403, statusText: 'Forbidden' });
    await settle();

    expect(errors).toEqual(['Seuls les organisateurs peuvent créer des événements.']);
  });

  it('prefills the form when editing and keeps untouched non-text attributes as they were', async () => {
    await open('/organizer/events/e1/edit');
    expect(text()).toContain('Chargement');

    httpMock.expectOne('/api/events/e1').flush(mine);
    await settle();

    expect(field('title').value).toBe('Concert Gnawa');
    expect(field('startDate').value).toBe(FUTURE_LOCAL);
    expect(field('totalSeats').value).toBe('200');

    submit();
    const req = httpMock.expectOne({ method: 'PUT', url: '/api/events/e1' });
    expect(req.request.body).toEqual({
      title: 'Concert Gnawa',
      description: 'Soirée musicale',
      type: 'CONCERT',
      city: 'Agadir',
      venue: 'Theatre de Verdure',
      startDate: FUTURE_ISO,
      totalSeats: 200,
      attributes: { artist: 'Maalem Hamid', ageLimit: 18 },
    });
    req.flush(mine);
    await settle();

    expect(successes).toEqual(['Événement mis à jour.']);
  });

  it('refuses to edit an event that belongs to someone else', async () => {
    await open('/organizer/events/e1/edit');
    httpMock.expectOne('/api/events/e1').flush({ ...mine, organizerId: 'someone-else' });
    await settle();

    expect(text()).toContain('propres événements');
    expect(root().querySelector('form')).toBeNull();
  });

  it('explains when the event to edit does not exist', async () => {
    await open('/organizer/events/e1/edit');
    httpMock.expectOne('/api/events/e1').flush(null, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(text()).toContain('n’existe pas');
  });
});
