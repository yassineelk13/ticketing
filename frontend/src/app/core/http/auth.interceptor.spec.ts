import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { makeToken } from '../../testing/make-token';
import { TOKEN_KEY } from '../auth/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function logIn(): string {
    const token = makeToken({
      sub: 'u1',
      email: 'a@b.com',
      role: 'USER',
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    sessionStorage.setItem(TOKEN_KEY, token);
    return token;
  }

  it('adds the bearer token to API calls when logged in', () => {
    const token = logIn();
    http.get('/api/events').subscribe();
    const req = httpMock.expectOne('/api/events');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    req.flush([]);
  });

  it('adds nothing when logged out', () => {
    http.get('/api/events').subscribe();
    const req = httpMock.expectOne('/api/events');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('never sends the token to another origin', () => {
    logIn();
    http.get('https://other.example.com/data').subscribe();
    const req = httpMock.expectOne('https://other.example.com/data');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});
