import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { EventResponse } from '../../../core/models/event.model';
import { EventDetail } from './event-detail';

const gnawa: EventResponse = {
  id: '6ada7a51fa81b0facca6ffd1',
  title: 'Concert Gnawa',
  description: 'Soirée musicale',
  type: 'CONCERT',
  city: 'Agadir',
  venue: 'Theatre de Verdure',
  startDate: '2026-12-20T20:00:00Z',
  totalSeats: 200,
  organizerId: 'o1',
  attributes: { artist: 'Maalem Hamid' },
};

describe('EventDetail', () => {
  let httpMock: HttpTestingController;
  let harness: RouterTestingHarness;

  const root = (): HTMLElement => harness.fixture.nativeElement;
  const text = (): string => root().textContent ?? '';

  async function open(id: string): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/events/${id}`, EventDetail);
    harness.detectChanges();
  }

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'events/:id', component: EventDetail }],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads the event whose id is in the URL and shows its details and attributes', async () => {
    await open(gnawa.id);
    expect(text()).toContain('Chargement');

    httpMock.expectOne(`/api/events/${gnawa.id}`).flush(gnawa);
    await settle();

    expect(root().querySelector('h1')?.textContent).toContain('Concert Gnawa');
    expect(text()).toContain('Theatre de Verdure, Agadir');
    expect(text()).toContain('200 places au total');
    expect(text()).toContain('Artist');
    expect(text()).toContain('Maalem Hamid');
  });

  it('shows a fallback when there is no description and hides empty attributes', async () => {
    await open('2');
    httpMock
      .expectOne('/api/events/2')
      .flush({ ...gnawa, id: '2', description: null, attributes: {} });
    await settle();

    expect(text()).toContain('Aucune description.');
    expect(text()).not.toContain('Informations complémentaires');
  });

  it('explains when the event does not exist (404)', async () => {
    await open('unknown');
    httpMock.expectOne('/api/events/unknown').flush(null, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(root().querySelector('[role="alert"]')?.textContent).toContain('n’existe pas');
    expect(root().querySelector('button.retry')).toBeNull();
  });

  it('shows an error with a retry button on other failures, and recovers', async () => {
    await open(gnawa.id);
    httpMock
      .expectOne(`/api/events/${gnawa.id}`)
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    await settle();
    expect(root().querySelector('[role="alert"]')?.textContent).toContain('Impossible');

    root().querySelector<HTMLButtonElement>('button.retry')?.click();
    httpMock.expectOne(`/api/events/${gnawa.id}`).flush(gnawa);
    await settle();

    expect(root().querySelector('h1')?.textContent).toContain('Concert Gnawa');
  });
});
