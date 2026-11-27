import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { toUserMessage } from '../http/problem';
import { Role } from '../models/user.model';
import { NotificationService } from '../notification/notification.service';
import { authGuard, guestGuard, roleGuard } from './auth.guards';
import { AuthService } from './auth.service';

describe('auth guards', () => {
  let role: Role | null;
  let messages: string[];

  beforeEach(() => {
    role = null;
    messages = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { isAuthenticated: () => role !== null, role: () => role },
        },
        { provide: NotificationService, useValue: { error: (m: string) => messages.push(m) } },
      ],
    });
  });

  function run(guard: CanActivateFn, url = '/tickets'): unknown {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }

  function pathOf(result: unknown): string {
    expect(result).toBeInstanceOf(UrlTree);
    return (
      TestBed.inject(Router)
        .serializeUrl(result as UrlTree)
        .split('?')[0] ?? ''
    );
  }

  it('authGuard lets a logged-in user through', () => {
    role = 'USER';
    expect(run(authGuard)).toBe(true);
  });

  it('authGuard sends an anonymous user to login, remembering the target page', () => {
    const result = run(authGuard, '/tickets');
    expect(pathOf(result)).toBe('/login');
    expect((result as UrlTree).queryParams).toEqual({ returnUrl: '/tickets' });
  });

  it('guestGuard lets an anonymous user through', () => {
    expect(run(guestGuard)).toBe(true);
  });

  it('guestGuard sends a logged-in user to the events page', () => {
    role = 'USER';
    expect(pathOf(run(guestGuard))).toBe('/events');
  });

  it('roleGuard lets the right role through', () => {
    role = 'ORGANIZER';
    expect(run(roleGuard('ORGANIZER'))).toBe(true);
  });

  it('roleGuard refuses the wrong role with a message', () => {
    role = 'USER';
    expect(pathOf(run(roleGuard('ORGANIZER')))).toBe('/events');
    expect(messages).toEqual([toUserMessage(403)]);
  });

  it('roleGuard sends an anonymous user to login', () => {
    expect(pathOf(run(roleGuard('ORGANIZER'), '/organizer'))).toBe('/login');
    expect(messages).toEqual([]);
  });
});
