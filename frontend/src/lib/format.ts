/** Money arrives from the API as integer paise and is only ever formatted for display. */
export function formatMoney(paise: number | null | undefined): string {
  if (paise === null || paise === undefined) {
    return '—'
  }
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(paise / 100)
}

export function formatPriceRange(min: number | null, max: number | null): string {
  if (min === null || max === null) {
    return '—'
  }
  return min === max ? formatMoney(min) : `${formatMoney(min)} – ${formatMoney(max)}`
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })
}

/** mm:ss, for the hold and drop countdowns. */
export function formatCountdown(totalSeconds: number): string {
  const clamped = Math.max(0, Math.floor(totalSeconds))
  const minutes = Math.floor(clamped / 60)
  const seconds = clamped % 60
  if (minutes >= 60) {
    const hours = Math.floor(minutes / 60)
    return `${hours}h ${String(minutes % 60).padStart(2, '0')}m`
  }
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export function formatWait(seconds: number): string {
  if (seconds < 60) {
    return 'less than a minute'
  }
  const minutes = Math.round(seconds / 60)
  return minutes === 1 ? 'about a minute' : `about ${minutes} minutes`
}
