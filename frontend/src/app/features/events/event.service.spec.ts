import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EventService } from './event.service';

describe('EventService', () => {
  let service: EventService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EventService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('calls the API without parameters when there is no filter', () => {
    service.list().subscribe();
    const req = httpMock.expectOne('/api/events');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([]);
  });

  it('sends the trimmed city and the date as query parameters', () => {
    service.list({ city: '  Agadir ', date: '2026-12-20' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/events');
    expect(req.request.params.get('city')).toBe('Agadir');
    expect(req.request.params.get('date')).toBe('2026-12-20');
    req.flush([]);
  });

  it('omits blank filters', () => {
    service.list({ city: '   ', date: '' }).subscribe();
    const req = httpMock.expectOne('/api/events');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([]);
  });

  it('fetches one event by id', () => {
    service.get('6ada7a51fa81b0facca6ffd1').subscribe();
    const req = httpMock.expectOne('/api/events/6ada7a51fa81b0facca6ffd1');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('encodes the id in the URL', () => {
    service.get('a/b').subscribe();
    httpMock.expectOne('/api/events/a%2Fb').flush({});
  });
});
