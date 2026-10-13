import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/endpoints'
import { Countdown } from '../components/Countdown'
import { ErrorBanner } from '../components/ErrorBanner'
import { Spinner } from '../components/Spinner'
import { EventStatusBadge } from '../components/StatusBadge'
import { formatDateTime, formatPriceRange } from '../lib/format'

export function EventsPage() {
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ['events'],
    queryFn: api.events,
    // A drop changes availability by the second.
    refetchInterval: 5000,
  })

  if (isPending) {
    return <Spinner label="Loading events" />
  }
  if (error) {
    return <ErrorBanner error={error} onRetry={refetch} />
  }
  if (!data || data.length === 0) {
    return (
      <div className="card empty">
        <h2>No events on sale</h2>
        <p className="muted">An organiser has not published anything yet.</p>
      </div>
    )
  }

  return (
    <>
      <div className="page-head">
        <h1>What&apos;s on</h1>
        <p className="muted">
          High-demand drops open with a virtual waiting room, so nobody gets ahead by refreshing faster.
        </p>
      </div>

      <ul className="event-grid">
        {data.map((event) => {
          const saleOpen = new Date(event.saleOpensAt).getTime() <= Date.now()
          return (
            <li key={event.id} className="card event-card">
              <div className="event-card-top">
                <EventStatusBadge status={event.status} />
                {event.highDemand ? <span className="badge badge-hot">High demand</span> : null}
              </div>
              <h2>
                <Link to={`/events/${event.id}`}>{event.title}</Link>
              </h2>
              <dl className="meta">
                <div>
                  <dt>When</dt>
                  <dd>{formatDateTime(event.startsAt)}</dd>
                </div>
                <div>
                  <dt>Where</dt>
                  <dd>
                    {event.venueName}, {event.venueCity}
                  </dd>
                </div>
                <div>
                  <dt>Tickets</dt>
                  <dd>{formatPriceRange(event.minPricePaise, event.maxPricePaise)}</dd>
                </div>
                <div>
                  <dt>Seats left</dt>
                  <dd>
                    {event.availableSeats} of {event.totalSeats}
                  </dd>
                </div>
              </dl>

              <div className="event-card-foot">
                {!saleOpen ? (
                  <Countdown target={event.saleOpensAt} prefix="Drops in" expiredLabel="Drop is live" />
                ) : (
                  <span className="muted small">
                    {event.highDemand ? 'Waiting room is open' : 'On sale now'}
                  </span>
                )}
                <Link
                  to={event.highDemand && saleOpen ? `/events/${event.id}/queue` : `/events/${event.id}`}
                  className="btn btn-primary btn-sm"
                >
                  {event.highDemand && saleOpen ? 'Join the queue' : 'View seats'}
                </Link>
              </div>
            </li>
          )
        })}
      </ul>
    </>
  )
}
