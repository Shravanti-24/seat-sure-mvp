import { formatCountdown } from '../lib/format'
import { useCountdown } from '../lib/useCountdown'

/** Shared by the "drops in" badge on the events page and the hold timer at checkout. */
export function Countdown({
  target,
  prefix,
  urgentBelowSeconds = 60,
  expiredLabel = 'Expired',
}: {
  target: string | null | undefined
  prefix?: string
  urgentBelowSeconds?: number
  expiredLabel?: string
}) {
  const secondsLeft = useCountdown(target)

  if (secondsLeft === null) {
    return null
  }
  if (secondsLeft === 0) {
    return <span className="countdown countdown-expired">{expiredLabel}</span>
  }
  const urgent = secondsLeft <= urgentBelowSeconds
  return (
    <span className={urgent ? 'countdown countdown-urgent' : 'countdown'}>
      {prefix ? `${prefix} ` : ''}
      {formatCountdown(secondsLeft)}
    </span>
  )
}
