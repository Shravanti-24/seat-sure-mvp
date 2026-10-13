import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/endpoints'
import { Countdown } from '../components/Countdown'
import { ErrorBanner } from '../components/ErrorBanner'
import { friendlyMessage } from '../lib/errors'
import { Spinner } from '../components/Spinner'
import { BookingStatusBadge } from '../components/StatusBadge'
import { formatDateTime, formatMoney } from '../lib/format'
import { useCountdown } from '../lib/useCountdown'
import { ApiError } from '../api/client'

export function CheckoutPage() {
  const { bookingId: bookingIdParam } = useParams<{ bookingId: string }>()
  const bookingId = Number(bookingIdParam)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [simulateFailure, setSimulateFailure] = useState(false)

  /**
   * One key per checkout attempt. Every retry of this attempt — a double click, a flaky network,
   * the user pressing Pay again — sends the same key, so the server can only ever charge once.
   */
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())

  const booking = useQuery({
    queryKey: ['booking', bookingId],
    queryFn: () => api.booking(bookingId),
    enabled: Number.isFinite(bookingId),
  })

  const secondsLeft = useCountdown(booking.data?.holdExpiresAt)
  const holdExpired = booking.data?.status === 'PENDING_PAYMENT' && secondsLeft === 0

  const pay = useMutation({
    mutationFn: () => api.pay(bookingId, idempotencyKey, simulateFailure),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['booking', bookingId] })
      if (result.status === 'SUCCESS') {
        navigate('/me/tickets', { replace: true })
      } else {
        // A declined card is a fresh attempt next time, so the key is rotated.
        setIdempotencyKey(crypto.randomUUID())
      }
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['booking', bookingId] })
    },
  })

  const release = useMutation({
    mutationFn: () => api.releaseHold(bookingId),
    onSuccess: () => navigate(`/events/${booking.data?.eventId ?? ''}`),
  })

  const seatSummary = useMemo(
    () =>
      (booking.data?.seats ?? [])
        .map((seat) => `${seat.sectionName} ${seat.label}`)
        .join(', '),
    [booking.data],
  )

  if (booking.isPending) {
    return <Spinner label="Loading your basket" />
  }
  if (booking.error) {
    return <ErrorBanner error={booking.error} onRetry={booking.refetch} />
  }
  const data = booking.data
  if (!data) {
    return <ErrorBanner error={new Error('That booking could not be found')} />
  }

  if (data.status === 'CONFIRMED') {
    return (
      <div className="narrow">
        <div className="card center">
          <h1>Paid and confirmed</h1>
          <p className="muted">
            {seatSummary} for {data.eventTitle}
          </p>
          <Link to="/me/tickets" className="btn btn-primary">
            See my tickets
          </Link>
        </div>
      </div>
    )
  }

  if (data.status !== 'PENDING_PAYMENT' || holdExpired) {
    return (
      <div className="narrow">
        <div className="card center">
          <h1>Your hold is gone</h1>
          <p className="muted">
            {holdExpired
              ? 'The seven minute hold ran out, so the seats went back on sale.'
              : `This booking is ${data.status.toLowerCase().replace('_', ' ')}.`}
          </p>
          <Link to={`/events/${data.eventId}`} className="btn btn-primary">
            Pick seats again
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="narrow">
      <div className="card">
        <div className="row between">
          <h1>Checkout</h1>
          <BookingStatusBadge status={data.status} />
        </div>

        <div className="banner banner-warning">
          <span>Seats held for you</span>
          <Countdown target={data.holdExpiresAt} urgentBelowSeconds={60} expiredLabel="Hold expired" />
        </div>

        <h2 className="small-head">{data.eventTitle}</h2>
        <p className="muted">
          {formatDateTime(data.eventStartsAt)} · {data.venueName}
        </p>

        <table className="table">
          <thead>
            <tr>
              <th>Section</th>
              <th>Seat</th>
              <th className="right">Price</th>
            </tr>
          </thead>
          <tbody>
            {data.seats.map((seat) => (
              <tr key={seat.eventSeatId}>
                <td>{seat.sectionName}</td>
                <td>{seat.label}</td>
                <td className="right">{formatMoney(seat.pricePaise)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th colSpan={2}>Total</th>
              <th className="right">{formatMoney(data.totalPaise)}</th>
            </tr>
          </tfoot>
        </table>

        {pay.error ? <ErrorBanner error={pay.error} /> : null}
        {pay.data && pay.data.status === 'FAILED' ? (
          <div className="banner banner-error" role="alert">
            {pay.data.message ?? 'The payment was declined.'} Your seats are still held — try again.
          </div>
        ) : null}
        {pay.error instanceof ApiError && pay.error.isGone ? (
          <Link to={`/events/${data.eventId}`} className="btn btn-ghost">
            Pick seats again
          </Link>
        ) : null}

        <label className="checkbox">
          <input
            type="checkbox"
            checked={simulateFailure}
            onChange={(event) => setSimulateFailure(event.target.checked)}
          />
          Simulate a declined card (for demos)
        </label>

        <div className="row gap">
          <button
            type="button"
            className="btn btn-primary"
            disabled={pay.isPending || release.isPending}
            onClick={() => pay.mutate()}
          >
            {pay.isPending ? 'Paying…' : `Pay ${formatMoney(data.totalPaise)}`}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={pay.isPending || release.isPending}
            onClick={() => release.mutate()}
          >
            Release my seats
          </button>
        </div>

        {release.error ? <p className="muted small">{friendlyMessage(release.error)}</p> : null}
        <p className="muted small">
          Pressing Pay twice is safe: both requests carry the same idempotency key, so the server
          charges once and returns the same booking.
        </p>
      </div>
    </div>
  )
}
