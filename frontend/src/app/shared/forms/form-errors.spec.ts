import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { applyServerErrors, fieldError } from './form-errors';

function makeForm() {
  return new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true }),
  });
}

describe('form-errors', () => {
  it('returns the message of the matching client error', () => {
    const form = makeForm();
    expect(fieldError(form.controls.email, { required: 'Obligatoire.' })).toBe('Obligatoire.');
  });

  it('returns null when the control is valid', () => {
    const form = makeForm();
    form.controls.email.setValue('a@b.com');
    expect(fieldError(form.controls.email, { required: 'Obligatoire.' })).toBeNull();
  });

  it('applies backend field errors to the matching controls and ignores unknown fields', () => {
    const form = makeForm();
    const error = new HttpErrorResponse({
      status: 400,
      error: { errors: { email: 'must be a well-formed email address', unknown: 'x' } },
    });
    expect(applyServerErrors(form, error)).toBe(true);
    expect(fieldError(form.controls.email, {})).toBe('must be a well-formed email address');
  });

  it('returns false when there is nothing to apply', () => {
    const error = new HttpErrorResponse({ status: 400, error: null });
    expect(applyServerErrors(makeForm(), error)).toBe(false);
  });
});
