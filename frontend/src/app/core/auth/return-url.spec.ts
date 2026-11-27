import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('keeps in-app paths', () => {
    expect(safeReturnUrl('/events/42?x=1')).toBe('/events/42?x=1');
  });

  it('falls back to the events page when missing', () => {
    expect(safeReturnUrl(null)).toBe('/events');
    expect(safeReturnUrl('')).toBe('/events');
  });

  it('rejects external and protocol-relative URLs', () => {
    expect(safeReturnUrl('https://evil.example')).toBe('/events');
    expect(safeReturnUrl('//evil.example')).toBe('/events');
    expect(safeReturnUrl('/\\evil.example')).toBe('/events');
  });
});
