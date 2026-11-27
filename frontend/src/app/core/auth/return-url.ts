const DEFAULT_URL = '/events';

/** Accepts only in-app paths, to prevent open redirects through ?returnUrl=. */
export function safeReturnUrl(value: string | null | undefined): string {
  if (value && value.startsWith('/') && !value.startsWith('//') && !value.includes('\\')) {
    return value;
  }
  return DEFAULT_URL;
}
