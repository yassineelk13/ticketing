export interface AttributeEntry {
  key: string;
  label: string;
  value: string;
}

/** "doorsOpen" -> "Doors Open", "age_limit" -> "Age limit" */
export function humanizeKey(key: string): string {
  const spaced = key
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .trim();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

export function formatAttributeValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '—';
  }
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'boolean') {
    return value ? 'Oui' : 'Non';
  }
  if (typeof value === 'number') {
    return String(value);
  }
  return JSON.stringify(value);
}

export function attributeEntries(
  attributes: Record<string, unknown> | null | undefined,
): AttributeEntry[] {
  return Object.entries(attributes ?? {}).map(([key, value]) => ({
    key,
    label: humanizeKey(key),
    value: formatAttributeValue(value),
  }));
}
