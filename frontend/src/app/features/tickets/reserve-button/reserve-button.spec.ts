import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TOKEN_KEY } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/notification/notification.service';
import { makeToken } from '../../../testing/make-token';
import { reservationErrorMessage } from '../reservation-errors';
import { ReserveButton } from './reserve-button';

const ticket = {
  id: 't1',
  eventId: 'e1',
  status: 'RESERVED',
  createdAt: '2026-12-01T10:00:00Z',
};

describe('ReserveButton', () => {
  let httpMock: HttpTestingController;
  let fixture: ComponentFixture<ReserveButton>;
  let messages: string[];

  const root = (): HTMLElement => fixture.nativeElement;
  const button = (): HTMLButtonElement => {
    const element = root().querySelector<HTMLButtonElement>('button.reserve');
    if (!element) {
      throw new Error('Reserve button not found');
    }
    return element;
  };

  function logIn(): void {
    sessionStorage.setItem(
      TOKEN_KEY,
      makeToken({
        sub: 'u1',
        email: 'a@b.com',
        role: 'USER',
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
  }

  function create(): void {
    fixture = TestBed.createComponent(ReserveButton);
    fixture.componentRef.setInput('eventId', 'e1');
    fixture.detectChanges();
  }

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    sessionStorage.clear();
    messages = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { error: (m: string) => messages.push(m) } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('invites an anonymous visitor to log in and come back to the event', () => {
    create();

    expect(root().querySelector('button.reserve')).toBeNull();
    const link = root().querySelector<HTMLAnchorElement>('a.login');
    expect(link?.textContent).toContain('Se connecter pour réserver');
    const url = new URL(link?.getAttribute('href') ?? '', 'http://localhost');
    expect(url.pathname).toBe('/login');
    expect(url.searchParams.get('returnUrl')).toBe('/events/e1');
  });

  it('disables the button while the request is in flight', async () => {
    logIn();
    create();

    button().click();
    fixture.detectChanges();
    expect(button().disabled).toBe(true);

    httpMock
      .expectOne('/api/tickets/reserve')
      .flush(ticket, { status: 201, statusText: 'Created' });
    await settle();
    expect(button().disabled).toBe(false);
  });

  it('reserves a place and confirms that payment is being processed', async () => {
    logIn();
    create();

    button().click();
    const req = httpMock.expectOne('/api/tickets/reserve');
    expect(req.request.body).toEqual({ eventId: 'e1' });
    req.flush(ticket, { status: 201, statusText: 'Created' });
    await settle();

    expect(root().querySelector('[role="status"]')?.textContent).toContain('Place réservée');
    expect(messages).toEqual([]);
    expect(root().querySelector('a[href="/tickets"]')).not.toBeNull();
  });

  it.each([
    [409, 'complet'],
    [404, 'n’existe plus'],
    [503, 'indisponible'],
  ])('shows a clear message on a %i response', async (status, expected) => {
    logIn();
    create();

    button().click();
    httpMock.expectOne('/api/tickets/reserve').flush(null, { status, statusText: 'Error' });
    await settle();

    expect(messages).toEqual([reservationErrorMessage(status)]);
    expect(messages[0]).toContain(expected);
    expect(button().disabled).toBe(false);
    expect(root().querySelector('[role="status"]')?.textContent?.trim()).toBe('');
  });
});
