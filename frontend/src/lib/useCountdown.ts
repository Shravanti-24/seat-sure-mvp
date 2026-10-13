import { useEffect, useState } from 'react'

/** Seconds remaining until the given instant, ticking once a second. Null target means no timer. */
export function useCountdown(targetIso: string | null | undefined): number | null {
  const [secondsLeft, setSecondsLeft] = useState<number | null>(() => remaining(targetIso))

  useEffect(() => {
    setSecondsLeft(remaining(targetIso))
    if (!targetIso) {
      return
    }
    const timer = window.setInterval(() => setSecondsLeft(remaining(targetIso)), 1000)
    return () => window.clearInterval(timer)
  }, [targetIso])

  return secondsLeft
}

function remaining(targetIso: string | null | undefined): number | null {
  if (!targetIso) {
    return null
  }
  return Math.max(0, Math.round((new Date(targetIso).getTime() - Date.now()) / 1000))
}
