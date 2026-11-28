import { FormArray, FormControl, FormGroup } from '@angular/forms';
import {
  buildAttributes,
  futureDate,
  integerValidator,
  toAttributeRows,
  toIsoUtc,
  toLocalInput,
  uniqueAttributeKeys,
} from './event-form-utils';

const row = (key: string, value: string) =>
  new FormGroup({ key: new FormControl(key), value: new FormControl(value) });

describe('date conversion', () => {
  it('converts a local date to a UTC ISO string', () => {
    expect(toIsoUtc('2026-12-20T20:00')).toBe(new Date('2026-12-20T20:00').toISOString());
  });

  it('round-trips between the input value and the API value', () => {
    const iso = toIsoUtc('2026-12-20T20:00');
    expect(iso).not.toBeNull();
    expect(toLocalInput(iso ?? '')).toBe('2026-12-20T20:00');
  });

  it('returns null or an empty string for unreadable values', () => {
    expect(toIsoUtc('')).toBeNull();
    expect(toIsoUtc('garbage')).toBeNull();
    expect(toLocalInput('garbage')).toBe('');
  });
});

describe('futureDate', () => {
  const validator = futureDate(() => Date.parse('2026-06-01T00:00:00Z'));

  it('accepts a later date', () => {
    expect(validator(new FormControl('2026-12-20T20:00'))).toBeNull();
  });

  it('rejects a past date', () => {
    expect(validator(new FormControl('2026-01-01T10:00'))).toEqual({ future: true });
  });

  it('leaves an empty value to the required validator', () => {
    expect(validator(new FormControl(''))).toBeNull();
  });

  it('rejects an unreadable date', () => {
    expect(validator(new FormControl('abc'))).toEqual({ invalidDate: true });
  });
});

describe('integerValidator', () => {
  it('accepts integers and empty values, rejects decimals', () => {
    expect(integerValidator(new FormControl(5))).toBeNull();
    expect(integerValidator(new FormControl(null))).toBeNull();
    expect(integerValidator(new FormControl(2.5))).toEqual({ integer: true });
  });
});

describe('uniqueAttributeKeys', () => {
  it('accepts distinct keys and ignores blank ones', () => {
    expect(uniqueAttributeKeys(new FormArray([row('a', '1'), row('b', '2')]))).toBeNull();
    expect(uniqueAttributeKeys(new FormArray([row('', ''), row('', '')]))).toBeNull();
  });

  it('rejects duplicates, even with different spacing', () => {
    expect(uniqueAttributeKeys(new FormArray([row('a', '1'), row(' a ', '2')]))).toEqual({
      duplicateKeys: true,
    });
  });
});

describe('attributes', () => {
  it('builds rows from attributes of any type', () => {
    expect(toAttributeRows({ artist: 'X', ageLimit: 18 })).toEqual([
      { key: 'artist', value: 'X' },
      { key: 'ageLimit', value: '18' },
    ]);
    expect(toAttributeRows(null)).toEqual([]);
  });

  it('trims keys and ignores rows without a key', () => {
    expect(
      buildAttributes(
        [
          { key: ' artist ', value: 'Stormy' },
          { key: '  ', value: 'lost' },
        ],
        null,
      ),
    ).toEqual({ artist: 'Stormy' });
  });

  it('keeps the original type of an untouched non-text value', () => {
    expect(buildAttributes([{ key: 'ageLimit', value: '18' }], { ageLimit: 18 })).toEqual({
      ageLimit: 18,
    });
  });

  it('saves an edited value as text', () => {
    expect(buildAttributes([{ key: 'ageLimit', value: '21' }], { ageLimit: 18 })).toEqual({
      ageLimit: '21',
    });
  });
});
