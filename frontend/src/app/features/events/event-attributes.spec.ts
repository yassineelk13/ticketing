import { attributeEntries, formatAttributeValue, humanizeKey } from './event-attributes';

describe('event attributes', () => {
  it('humanizes keys', () => {
    expect(humanizeKey('artist')).toBe('Artist');
    expect(humanizeKey('doorsOpen')).toBe('Doors Open');
    expect(humanizeKey('age_limit')).toBe('Age limit');
  });

  it('formats values of any type as text', () => {
    expect(formatAttributeValue('Stormy')).toBe('Stormy');
    expect(formatAttributeValue(18)).toBe('18');
    expect(formatAttributeValue(true)).toBe('Oui');
    expect(formatAttributeValue(false)).toBe('Non');
    expect(formatAttributeValue(null)).toBe('—');
    expect(formatAttributeValue(['a', 'b'])).toBe('["a","b"]');
  });

  it('builds entries and tolerates missing attributes', () => {
    expect(attributeEntries({ artist: 'Stormy' })).toEqual([
      { key: 'artist', label: 'Artist', value: 'Stormy' },
    ]);
    expect(attributeEntries(null)).toEqual([]);
    expect(attributeEntries({})).toEqual([]);
  });

  it('shows markup as plain text data, never as HTML', () => {
    expect(formatAttributeValue('<script>alert(1)</script>')).toBe('<script>alert(1)</script>');
  });
});
