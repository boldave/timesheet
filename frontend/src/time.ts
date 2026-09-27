const pad = (n: number) => String(n).padStart(2, '0')

/** Data lokalna jako YYYY-MM-DD (bez przeliczania na UTC). */
export function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Godzina lokalna jako HH:mm. */
export function toHHmm(d: Date): string {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Czy zaznaczenie [start, end) mieści się w jednym dniu kalendarzowym. */
export function withinOneDay(start: Date, end: Date): boolean {
  return toIsoDate(start) === toIsoDate(new Date(end.getTime() - 1))
}
