import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../models/problem-detail.model';

/** Reads a ProblemDetail (RFC 9457). Spring Security 401/403 have an empty body, so this can be null. */
export function parseProblem(error: HttpErrorResponse): ProblemDetail | null {
  const body: unknown = error.error;
  if (error.status === 0 || typeof body !== 'object' || body === null) {
    return null;
  }
  return body as ProblemDetail;
}

const MESSAGES: Partial<Record<number, string>> = {
  400: 'Données invalides. Vérifiez les champs du formulaire.',
  401: 'Identifiants invalides ou session expirée.',
  403: 'Accès refusé : vous n’avez pas les droits nécessaires.',
  404: 'Ressource introuvable.',
  409: 'Conflit : l’opération n’est pas possible dans l’état actuel.',
  503: 'Service momentanément indisponible. Réessayez dans un instant.',
};

export function toUserMessage(status: number): string {
  if (status === 0) {
    return 'Impossible de joindre le serveur. Vérifiez votre connexion.';
  }
  return (
    MESSAGES[status] ??
    (status >= 500 ? 'Erreur serveur. Réessayez plus tard.' : 'Une erreur est survenue.')
  );
}
