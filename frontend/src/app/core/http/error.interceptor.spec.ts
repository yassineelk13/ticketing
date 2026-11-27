import {
  HttpClient,
  HttpContext,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { makeToken } from '../../testing/make-token';
import { AuthService, TOKEN_KEY } from '../auth/auth.service';
import { NotificationService } from '../notification/notification.service';
import { SILENT_ERRORS } from './api-context';
import { errorInterceptor } from './error.interceptor';
import { toUserMessage } from './problem';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let messages: string[];
  let navigations: unknown[][];

  beforeEach(() => {
    sessionStorage.clear();
    messages = [];
    navigations = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { error: (m: string) => messages.push(m) } },
        {
          provide: Router,
          useValue: {
            url: '/events/42',
            navigate: (...args: unknown[]) => {
              navigations.push(args);
              return Promise.resolve(true);
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

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

  it('shows a French message and rethrows the error to the caller', () => {
    let status = 0;
    http.get('/api/events').subscribe({
      error: (e: HttpErrorResponse) => {
        status = e.status;
      },
    });
    httpMock.expectOne('/api/events').flush(null, { status: 503, statusText: 'Unavailable' });

    expect(status).toBe(503);
    expect(messages).toEqual([toUserMessage(503)]);
  });

  it('shows the network message when the server is unreachable', () => {
    http.get('/api/events').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/events').error(new ProgressEvent('error'));
    expect(messages).toEqual([toUserMessage(0)]);
  });

  it('logs out and redirects to login on a 401 while authenticated', () => {
    logIn();
    const auth = TestBed.inject(AuthService);
    expect(auth.isAuthenticated()).toBe(true);

    http.get('/api/tickets/my-tickets').subscribe({ error: () => undefined });
    httpMock
      .expectOne('/api/tickets/my-tickets')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigations).toEqual([[['/login'], { queryParams: { returnUrl: '/events/42' } }]]);
    expect(messages).toEqual(['Votre session a expiré. Veuillez vous reconnecter.']);
  });

  it('does not redirect on a 401 when nobody is logged in (bad credentials)', () => {
    http.post('/api/users/login', {}).subscribe({ error: () => undefined });
    httpMock.expectOne('/api/users/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(navigations).toEqual([]);
    expect(messages).toEqual([toUserMessage(401)]);
  });

  it('stays silent when the request opts out with SILENT_ERRORS', () => {
    http
      .get('/api/events/1', { context: new HttpContext().set(SILENT_ERRORS, true) })
      .subscribe({ error: () => undefined });
    httpMock.expectOne('/api/events/1').flush(null, { status: 404, statusText: 'Not Found' });

    expect(messages).toEqual([]);
  });
});
