import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TOKEN_KEY } from '../../../core/auth/auth.service';
import { EventResponse } from '../../../core/models/event.model';
import { makeToken } from '../../../testing/make-token';
import { MyEvents } from './my-events';

const event = (
  id: string,
  title: string,
  organizerId: string,
  startDate: string,
): EventResponse => ({
  id,
  title,
  description: null,
  type: 'CONCERT',
  city: 'Agadir',
  venue: 'Theatre de Verdure',
  startDate,
  totalSeats: 100,
  organizerId,
  attributes: {},
});

describe('MyEvents', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<MyEvents>;

  const root = (): HTMLElement => fixture.nativeElement;
  const text = (): string => root().textContent ?? '';
  const titles = (): (string | undefined)[] =>
    Array.from(root().querySelectorAll('.event-title')).map((el) => el.textContent?.trim());

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
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
    TestBed.configureTestingModule({
      imports: [MyEvents],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MyEvents);
  });

  afterEach(() => httpMock.verify());

  it('shows only my events, soonest first', async () => {
    fixture.detectChanges();
    expect(text()).toContain('Chargement');

    httpMock
      .expectOne('/api/events')
      .flush([
        event('1', 'Zeta', 'o1', '2026-12-20T20:00:00Z'),
        event('2', 'Someone else', 'o2', '2026-12-01T20:00:00Z'),
        event('3', 'Alpha', 'o1', '2026-12-10T20:00:00Z'),
      ]);
    await settle();

    expect(titles()).toEqual(['Alpha', 'Zeta']);
    expect(text()).not.toContain('Someone else');
    expect(root().querySelector('a[href="/organizer/events/new"]')).not.toBeNull();
    expect(root().querySelector('a[href="/organizer/events/3/edit"]')).not.toBeNull();
  });

  it('shows an empty state when I have no event', async () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/events')
      .flush([event('2', 'Someone else', 'o2', '2026-12-01T20:00:00Z')]);
    await settle();

    expect(text()).toContain('Vous n’avez pas encore créé d’événement.');
  });

  it('shows an error state and recovers with the retry button', async () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/events')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    await settle();
    expect(root().querySelector('[role="alert"]')?.textContent).toContain('Impossible');

    root().querySelector<HTMLButtonElement>('button.retry')?.click();
    httpMock.expectOne('/api/events').flush([event('1', 'Alpha', 'o1', '2026-12-10T20:00:00Z')]);
    await settle();

    expect(titles()).toEqual(['Alpha']);
  });
});
