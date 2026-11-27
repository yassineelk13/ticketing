import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup } from '@angular/forms';
import { parseProblem } from '../../core/http/problem';

/** Returns the message to display for a control: a backend error first, then a client one. */
export function fieldError(
  control: AbstractControl,
  messages: Record<string, string>,
): string | null {
  const errors = control.errors;
  if (!errors) {
    return null;
  }
  const serverMessage = errors['server'];
  if (typeof serverMessage === 'string') {
    return serverMessage;
  }
  const key = Object.keys(errors).find((name) => name in messages);
  return key ? (messages[key] ?? null) : null;
}

/** Puts the backend validation errors ({field: message}) on the matching controls. */
export function applyServerErrors(form: FormGroup, error: HttpErrorResponse): boolean {
  const fieldErrors = parseProblem(error)?.errors;
  if (!fieldErrors) {
    return false;
  }
  let applied = false;
  for (const [name, message] of Object.entries(fieldErrors)) {
    const control = form.get(name);
    if (control) {
      control.setErrors({ server: message });
      control.markAsTouched();
      applied = true;
    }
  }
  return applied;
}
