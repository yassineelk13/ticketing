import { HttpErrorResponse } from '@angular/common/http';
import { parseProblem, toUserMessage } from './problem';

describe('problem helpers', () => {
  it('reads a ProblemDetail body', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { title: 'Bad Request', status: 400, errors: { email: 'must be valid' } },
    });
    expect(parseProblem(error)?.errors).toEqual({ email: 'must be valid' });
  });

  it('returns null for an empty body (Spring Security 401/403)', () => {
    expect(parseProblem(new HttpErrorResponse({ status: 401, error: null }))).toBeNull();
  });

  it('returns null for network errors', () => {
    const error = new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') });
    expect(parseProblem(error)).toBeNull();
  });

  it('maps statuses to French messages with fallbacks', () => {
    expect(toUserMessage(0)).toContain('serveur');
    expect(toUserMessage(403)).toContain('refusé');
    expect(toUserMessage(500)).toContain('Erreur serveur');
    expect(toUserMessage(418)).toBe('Une erreur est survenue.');
  });
});
