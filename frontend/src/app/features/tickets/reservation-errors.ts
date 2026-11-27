import { toUserMessage } from '../../core/http/problem';

export function reservationErrorMessage(status: number): string {
  switch (status) {
    case 409:
      return 'Désolé, cet événement est complet.';
    case 404:
      return 'Cet événement n’existe plus.';
    case 401:
      return 'Votre session a expiré. Veuillez vous reconnecter.';
    default:
      return toUserMessage(status);
  }
}
