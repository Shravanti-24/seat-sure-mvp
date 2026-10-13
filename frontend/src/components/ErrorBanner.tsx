import { friendlyMessage } from '../lib/errors'

export function ErrorBanner({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) {
    return null
  }
  return (
    <div className="banner banner-error" role="alert">
      <span>{friendlyMessage(error)}</span>
      {onRetry ? (
        <button type="button" className="btn btn-ghost btn-sm" onClick={onRetry}>
          Try again
        </button>
      ) : null}
    </div>
  )
}
