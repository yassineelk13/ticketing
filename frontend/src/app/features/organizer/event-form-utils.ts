import { ValidatorFn } from '@angular/forms';
import { formatAttributeValue } from '../events/event-attributes';

export interface AttributeRow {
  key: string;
  value: string;
}

/** "2026-12-20T20:00" (local time, from datetime-local) -> UTC ISO string, or null if unreadable. */
export function toIsoUtc(local: string): string | null {
  if (!local) {
    return null;
  }
  const date = new Date(local);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

const pad = (value: number): string => String(value).padStart(2, '0');

/** UTC ISO string from the API -> value for datetime-local, in the browser's time zone. */
export function toLocalInput(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** The backend requires startDate to be in the future (@Future): warn before sending. */
export function futureDate(now: () => number = Date.now): ValidatorFn {
  return (control) => {
    const value = control.value as string | null;
    if (!value) {
      return null; // "required" handles the empty case
    }
    const iso = toIsoUtc(value);
    if (iso === null) {
      return { invalidDate: true };
    }
    return Date.parse(iso) > now() ? null : { future: true };
  };
}

export const integerValidator: ValidatorFn = (control) => {
  const value: unknown = control.value;
  if (value === null || value === '') {
    return null;
  }
  return Number.isInteger(value) ? null : { integer: true };
};

export const uniqueAttributeKeys: ValidatorFn = (control) => {
  const rows = control.value as AttributeRow[];
  const keys = rows.map((row) => row.key.trim()).filter((key) => key !== '');
  return new Set(keys).size === keys.length ? null : { duplicateKeys: true };
};

export function toAttributeRows(
  attributes: Record<string, unknown> | null | undefined,
): AttributeRow[] {
  return Object.entries(attributes ?? {}).map(([key, value]) => ({
    key,
    value: formatAttributeValue(value),
  }));
}

/**
 * Turns the rows into the attributes object. Values are typed as text, except an untouched
 * non-text value (a number, a boolean...) which keeps its original type, so editing never corrupts it.
 */
export function buildAttributes(
  rows: AttributeRow[],
  original: Record<string, unknown> | null | undefined,
): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const row of rows) {
    const key = row.key.trim();
    if (!key) {
      continue;
    }
    const previous = original?.[key];
    const untouched =
      previous !== undefined &&
      typeof previous !== 'string' &&
      formatAttributeValue(previous) === row.value;
    result[key] = untouched ? previous : row.value;
  }
  return result;
}
