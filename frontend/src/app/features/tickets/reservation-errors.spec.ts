import { toUserMessage } from '../../core/http/problem';
import { reservationErrorMessage } from './reservation-errors';

describe('reservationErrorMessage', () => {
  it('explains a full event (409)', () => {
    expect(reservationErrorMessage(409)).toContain('complet');
  });

  it('explains an unknown event (404)', () => {
    expect(reservationErrorMessage(404)).toContain('n’existe plus');
  });

  it('explains an expired session (401)', () => {
    expect(reservationErrorMessage(401)).toContain('session a expiré');
  });

  it('falls back to the common messages (503, network)', () => {
    expect(reservationErrorMessage(503)).toBe(toUserMessage(503));
    expect(reservationErrorMessage(0)).toBe(toUserMessage(0));
  });
});
