import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EventResponse } from '../../../core/models/event.model';
import { EventsList } from './events-list';

function makeEvent(id: string, title: string): EventResponse {
  return {
    id,
    title,
    description: null,
    type: 'CONCERT',
    city: 'Agadir',
    venue: 'Theatre de Verdure',
    startDate: '2026-12-20T20:00:00Z',
    totalSeats: 200,
    organizerId: 'o1',
    attributes: {},
  };
}

describe('EventsList', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<EventsList>;

  const root = (): HTMLElement => fixture.nativeElement;
  const text = (): string => root().textContent ?? '';

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EventsList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EventsList);
  });

  afterEach(() => httpMock.verify());

  it('shows a loading state, then the events', async () => {
    fixture.detectChanges();
    expect(text()).toContain('Chargement');

    httpMock
      .expectOne('/api/events')
      .flush([makeEvent('1', 'Concert Gnawa'), makeEvent('2', 'Rap marocaine')]);
    await settle();

    expect(root().querySelectorAll('.event-card').length).toBe(2);
    expect(text()).toContain('Concert Gnawa');
    expect(text()).not.toContain('Chargement');
  });

  it('shows an empty state when there is no event', async () => {
    httpMock.expectOne('/api/events').flush([]);
    await settle();

    expect(text()).toContain('Aucun événement pour le moment.');
  });

  it('shows an error state and recovers with the retry button', async () => {
    httpMock
      .expectOne('/api/events')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    await settle();
    expect(root().querySelector('[role="alert"]')?.textContent).toContain('Impossible');

    root().querySelector<HTMLButtonElement>('button.retry')?.click();
    httpMock.expectOne('/api/events').flush([makeEvent('1', 'Concert Gnawa')]);
    await settle();

    expect(text()).toContain('Concert Gnawa');
    expect(root().querySelector('[role="alert"]')).toBeNull();
  });

  it('searches with the city filter and explains an empty result', async () => {
    httpMock.expectOne('/api/events').flush([makeEvent('1', 'Concert Gnawa')]);
    await settle();

    const input = root().querySelector<HTMLInputElement>('input[formControlName="city"]');
    if (!input) {
      throw new Error('City input not found');
    }
    input.value = 'Paris';
    input.dispatchEvent(new Event('input'));
    root().querySelector('form')?.dispatchEvent(new Event('submit'));

    const req = httpMock.expectOne(
      (r) => r.url === '/api/events' && r.params.get('city') === 'Paris',
    );
    req.flush([]);
    await settle();

    expect(text()).toContain('Aucun événement ne correspond à votre recherche.');
  });
});
