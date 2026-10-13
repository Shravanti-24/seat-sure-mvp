import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { Countdown } from '../components/Countdown'
import { ErrorBanner } from '../components/ErrorBanner'
import { friendlyMessage } from '../lib/errors'
import { SeatMapView } from '../components/SeatMapView'
import { Spinner } from '../components/Spinner'
import { EventStatusBadge } from '../components/StatusBadge'
import { formatDateTime, formatMoney } from '../lib/format'
import type { SeatMapSeat } from '../api/types'
import { admissionTokenFor } from '../lib/admission'

export function EventDetailPage() {
  const { id } = useParams<{ id: string }>()
  const eventId = Number(id)
  const navigate = useNavigate()
  const { user } = useAuth()
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [holdError, setHoldError] = useState<unknown>(null)

  const eventQuery = useQuery({
    queryKey: ['event', eventId],
    queryFn: () => api.event(eventId),
    enabled: Number.isFinite(eventId),
  })

  const seatMapQuery = useQuery({
    queryKey: ['seatmap', eventId],
    queryFn: () => api.seatMap(eventId),
    enabled: Number.isFinite(eventId),
    // The spec's three seconds: fast enough to see seats go, slow enough to be kind to the API.
    refetchInterval: 3000,
  })

  const hold = useMutation({
    mutationFn: (seatIds: number[]) => api.hold(eventId, seatIds, admissionTokenFor(eventId)),
    onSuccess: (booking) => navigate(`/checkout/${booking.bookingId}`),
    onError: (error) => {
      setHoldError(error)
      // Someone else took a seat we had selected: drop the stale picks and show fresh truth.
      setSelected(new Set())
      void seatMapQuery.refetch()
    },
  })

  const seatMap = seatMapQuery.data
  const priceBySeat = useMemo(() => {
    const prices = new Map<number, number>()
    for (const section of seatMap?.sections ?? []) {
      for (const seat of section.seats) {
        prices.set(seat.seatId, section.pricePaise)
      }
    }
    return prices
  }, [seatMap])

  const total = [...selected].reduce((sum, seatId) => sum + (priceBySeat.get(seatId) ?? 0), 0)

  if (eventQuery.isPending || seatMapQuery.isPending) {
    return <Spinner label="Loading the seat map" />
  }
  if (eventQuery.error) {
    return <ErrorBanner error={eventQuery.error} onRetry={eventQuery.refetch} />
  }
  if (!eventQuery.data || !seatMap) {
    return <ErrorBanner error={new Error('That event could not be loaded')} />
  }

  const summary = eventQuery.data.summary
  const saleOpen = new Date(summary.saleOpensAt).getTime() <= Date.now()
  const maxSeats = summary.maxSeatsPerUser
  const needsQueue = summary.highDemand && !admissionTokenFor(eventId)

  function toggleSeat(seat: SeatMapSeat) {
    setHoldError(null)
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(seat.seatId)) {
        next.delete(seat.seatId)
      } else if (next.size < maxSeats) {
        next.add(seat.seatId)
      }
      return next
    })
  }

  return (
    <>
      <div className="page-head">
        <div className="row gap">
          <EventStatusBadge status={summary.status} />
          {summary.highDemand ? <span className="badge badge-hot">High demand</span> : null}
        </div>
        <h1>{summary.title}</h1>
        <p className="muted">
          {formatDateTime(summary.startsAt)} · {summary.venueName}, {summary.venueCity}
        </p>
        {eventQuery.data.description ? <p>{eventQuery.data.description}</p> : null}
        <ul className="price-list">
          {eventQuery.data.prices.map((price) => (
            <li key={price.sectionId}>
              <strong>{price.sectionName}</strong> {formatMoney(price.pricePaise)}
            </li>
          ))}
        </ul>
      </div>

      {!saleOpen ? (
        <div className="banner banner-info">
          <span>Tickets are not on sale yet.</span>
          <Countdown target={summary.saleOpensAt} prefix="Opens in" expiredLabel="Opening now" />
        </div>
      ) : null}

      {saleOpen && needsQueue ? (
        <div className="banner banner-info">
          <span>This drop uses a waiting room. Join the queue to get your turn.</span>
          <Link to={`/events/${eventId}/queue`} className="btn btn-primary btn-sm">
            Join the queue
          </Link>
        </div>
      ) : null}

      {summary.status === 'SOLD_OUT' ? (
        <div className="banner banner-info">This event is sold out.</div>
      ) : null}

      {holdError ? <ErrorBanner error={holdError} /> : null}

      <SeatMapView
        sections={seatMap.sections}
        selected={selected}
        onToggle={toggleSeat}
        disabled={!saleOpen || !user || user.role !== 'ATTENDEE'}
      />

      <div className="sticky-bar">
        <div>
          <strong>
            {selected.size} of {maxSeats} seats
          </strong>
          <span className="muted small"> · {formatMoney(total)}</span>
        </div>
        {!user ? (
          <Link to="/login" state={{ from: `/events/${eventId}` }} className="btn btn-primary">
            Sign in to book
          </Link>
        ) : user.role !== 'ATTENDEE' ? (
          <span className="muted small">Only attendee accounts can book</span>
        ) : (
          <button
            type="button"
            className="btn btn-primary"
            disabled={selected.size === 0 || hold.isPending || !saleOpen}
            onClick={() => hold.mutate([...selected])}
          >
            {hold.isPending ? 'Holding…' : `Hold ${selected.size || ''} seat${selected.size === 1 ? '' : 's'}`}
          </button>
        )}
      </div>

      {hold.error ? <p className="muted small">{friendlyMessage(hold.error)}</p> : null}
    </>
  )
}
