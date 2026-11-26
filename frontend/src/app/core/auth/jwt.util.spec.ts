import { makeToken } from '../../testing/make-token';
import { decodeJwt, isExpired, JwtClaims } from './jwt.util';

const claims: JwtClaims = { sub: 'user-1', email: 'a@b.com', role: 'USER', exp: 2_000_000_000 };

describe('jwt.util', () => {
  it('decodes a valid token', () => {
    expect(decodeJwt(makeToken(claims))).toEqual(claims);
  });

  it('returns null for malformed tokens', () => {
    expect(decodeJwt('not-a-jwt')).toBeNull();
    expect(decodeJwt('a.b.c')).toBeNull();
  });

  it('returns null when the role is unknown', () => {
    expect(decodeJwt(makeToken({ ...claims, role: 'ADMIN' }))).toBeNull();
  });

  it('detects expiry', () => {
    expect(isExpired(claims, claims.exp * 1000 - 1)).toBe(false);
    expect(isExpired(claims, claims.exp * 1000)).toBe(true);
  });
});
