/**
 * The admission token the waiting room handed out. Kept in sessionStorage so a reload on the way to
 * the seat map does not cost the user their place; it is scoped per event and expires server side
 * after ten minutes regardless of what the browser remembers.
 */
const KEY_PREFIX = 'seatsure.admission.'

export function rememberAdmissionToken(eventId: number, token: string): void {
  try {
    window.sessionStorage.setItem(`${KEY_PREFIX}${eventId}`, token)
  } catch {
    // Nothing to do: the queue page keeps the token in memory for this tab anyway.
  }
}

export function admissionTokenFor(eventId: number): string | null {
  try {
    return window.sessionStorage.getItem(`${KEY_PREFIX}${eventId}`)
  } catch {
    return null
  }
}

export function forgetAdmissionToken(eventId: number): void {
  try {
    window.sessionStorage.removeItem(`${KEY_PREFIX}${eventId}`)
  } catch {
    // Ignore.
  }
}
