import { Role } from '../models/user.model';

export interface JwtClaims {
  sub: string;
  email: string;
  role: Role;
  exp: number; // seconds since epoch
}

/** Decodes the payload of a JWT. It does NOT verify the signature (only the backend can). */
export function decodeJwt(token: string): JwtClaims | null {
  const parts = token.split('.');
  const payloadPart = parts[1];
  if (parts.length !== 3 || !payloadPart) {
    return null;
  }
  try {
    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
    const { sub, email, role, exp } = JSON.parse(new TextDecoder().decode(bytes)) as Record<
      string,
      unknown
    >;
    if (typeof sub !== 'string' || typeof email !== 'string' || typeof exp !== 'number') {
      return null;
    }
    if (role !== 'USER' && role !== 'ORGANIZER') {
      return null;
    }
    return { sub, email, role, exp };
  } catch {
    return null;
  }
}

export function isExpired(claims: JwtClaims, now: number = Date.now()): boolean {
  return claims.exp * 1000 <= now;
}
