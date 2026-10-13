import { ApiError } from '../api/client'

/**
 * Turns a thrown error into language an attendee understands. The three cases that matter during a
 * drop get their own wording: someone else took the seat, the hold expired, or the user is going
 * too fast.
 */
export function friendlyMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.isConflict && error.details.length > 0) {
      return `${error.message}. Pick another seat and try again.`
    }
    if (error.isGone) {
      return 'Your hold expired, so the seats went back on sale. Choose your seats again.'
    }
    if (error.isRateLimited) {
      return 'You are going too fast. Wait a few seconds and try again.'
    }
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Something went wrong'
}
