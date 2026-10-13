import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/endpoints'
import { ErrorBanner } from '../components/ErrorBanner'
import { friendlyMessage } from '../lib/errors'
import { Spinner } from '../components/Spinner'
import { EventStatusBadge } from '../components/StatusBadge'
import { formatDateTime, formatMoney, formatWait } from '../lib/format'

export function AdminPage() {
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null)

  const events = useQuery({ queryKey: ['admin-events'], queryFn: api.adminEvents })
  const venues = useQuery({ queryKey: ['admin-venues'], queryFn: api.adminVenues })

  return (
    <>
      <div className="page-head">
        <h1>Organiser console</h1>
        <p className="muted">Create venues and events, then watch how a drop actually went.</p>
      </div>

      <div className="admin-grid">
        <section className="card">
          <h2>Events</h2>
          {events.isPending ? <Spinner /> : null}
          {events.error ? <ErrorBanner error={events.error} onRetry={events.refetch} /> : null}
          <ul className="admin-list">
            {(events.data ?? []).map((event) => (
              <li key={event.id}>
                <button
                  type="button"
                  className={selectedEventId === event.id ? 'admin-item admin-item-active' : 'admin-item'}
                  onClick={() => setSelectedEventId(event.id)}
                >
                  <span>
                    <strong>{event.title}</strong>
                    <span className="muted small">
                      {' '}
                      {formatDateTime(event.startsAt)}
                    </span>
                  </span>
                  <span className="row gap">
                    {event.highDemand ? <span className="badge badge-hot">Hot</span> : null}
                    <EventStatusBadge status={event.status} />
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>

        <CreateVenueCard onCreated={() => void venues.refetch()} />
        <CreateEventCard venues={venues.data ?? []} onCreated={() => void events.refetch()} />
      </div>

      {selectedEventId ? <EventReportCard eventId={selectedEventId} /> : null}
    </>
  )
}

function CreateVenueCard({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('')
  const [city, setCity] = useState('')
  const [rows, setRows] = useState(5)
  const [seatsPerRow, setSeatsPerRow] = useState(20)

  const create = useMutation({
    mutationFn: () =>
      api.adminCreateVenue({
        name,
        city,
        sections: [{ name: 'General', rowCount: rows, seatsPerRow }],
      }),
    onSuccess: () => {
      setName('')
      setCity('')
      onCreated()
    },
  })

  return (
    <section className="card">
      <h2>New venue</h2>
      <form
        className="form"
        onSubmit={(event) => {
          event.preventDefault()
          create.mutate()
        }}
      >
        <label>
          Name
          <input value={name} onChange={(event) => setName(event.target.value)} required />
        </label>
        <label>
          City
          <input value={city} onChange={(event) => setCity(event.target.value)} required />
        </label>
        <div className="row gap">
          <label>
            Rows
            <input
              type="number"
              min={1}
              max={60}
              value={rows}
              onChange={(event) => setRows(Number(event.target.value))}
            />
          </label>
          <label>
            Seats per row
            <input
              type="number"
              min={1}
              max={60}
              value={seatsPerRow}
              onChange={(event) => setSeatsPerRow(Number(event.target.value))}
            />
          </label>
        </div>
        <p className="muted small">Creates one &quot;General&quot; section with {rows * seatsPerRow} seats.</p>
        {create.error ? <div className="banner banner-error">{friendlyMessage(create.error)}</div> : null}
        {create.isSuccess ? <div className="banner banner-info">Venue created.</div> : null}
        <button type="submit" className="btn btn-primary btn-sm" disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create venue'}
        </button>
      </form>
    </section>
  )
}

function CreateEventCard({
  venues,
  onCreated,
}: {
  venues: { id: number; name: string; city: string; sections: { id: number; name: string }[] }[]
  onCreated: () => void
}) {
  const [venueId, setVenueId] = useState<number | ''>('')
  const [title, setTitle] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [saleOpensAt, setSaleOpensAt] = useState('')
  const [highDemand, setHighDemand] = useState(true)
  const [priceRupees, setPriceRupees] = useState(1500)

  const venue = venues.find((candidate) => candidate.id === venueId)

  const create = useMutation({
    mutationFn: () =>
      api.adminCreateEvent({
        venueId: Number(venueId),
        title,
        description: '',
        startsAt: new Date(startsAt).toISOString(),
        saleOpensAt: new Date(saleOpensAt).toISOString(),
        highDemand,
        maxSeatsPerUser: 6,
        status: 'ON_SALE',
        // Every section of the venue needs a price; the form uses one price for all of them.
        prices: (venue?.sections ?? []).map((section) => ({
          sectionId: section.id,
          pricePaise: priceRupees * 100,
        })),
      }),
    onSuccess: () => {
      setTitle('')
      onCreated()
    },
  })

  return (
    <section className="card">
      <h2>New event</h2>
      <form
        className="form"
        onSubmit={(event) => {
          event.preventDefault()
          create.mutate()
        }}
      >
        <label>
          Venue
          <select value={venueId} onChange={(event) => setVenueId(Number(event.target.value))} required>
            <option value="">Choose a venue…</option>
            {venues.map((option) => (
              <option key={option.id} value={option.id}>
                {option.name}, {option.city}
              </option>
            ))}
          </select>
        </label>
        <label>
          Title
          <input value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={200} />
        </label>
        <label>
          Starts at
          <input
            type="datetime-local"
            value={startsAt}
            onChange={(event) => setStartsAt(event.target.value)}
            required
          />
        </label>
        <label>
          Sale opens at
          <input
            type="datetime-local"
            value={saleOpensAt}
            onChange={(event) => setSaleOpensAt(event.target.value)}
            required
          />
        </label>
        <label>
          Price per seat (₹)
          <input
            type="number"
            min={0}
            value={priceRupees}
            onChange={(event) => setPriceRupees(Number(event.target.value))}
          />
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={highDemand}
            onChange={(event) => setHighDemand(event.target.checked)}
          />
          High demand (use the virtual waiting room)
        </label>
        {create.error ? <div className="banner banner-error">{friendlyMessage(create.error)}</div> : null}
        {create.isSuccess ? <div className="banner banner-info">Event created and on sale.</div> : null}
        <button type="submit" className="btn btn-primary btn-sm" disabled={create.isPending || !venueId}>
          {create.isPending ? 'Creating…' : 'Create event'}
        </button>
      </form>
    </section>
  )
}

function EventReportCard({ eventId }: { eventId: number }) {
  const queryClient = useQueryClient()
  const report = useQuery({
    queryKey: ['admin-report', eventId],
    queryFn: () => api.adminReport(eventId),
    refetchInterval: 5000,
  })

  if (report.isPending) {
    return <Spinner label="Building the report" />
  }
  if (report.error) {
    return <ErrorBanner error={report.error} onRetry={report.refetch} />
  }
  const data = report.data
  if (!data) {
    return null
  }

  const timeToSellOut =
    data.fairness.soldOutAt !== null
      ? Math.max(0, (new Date(data.fairness.soldOutAt).getTime() - new Date(data.saleOpensAt).getTime()) / 1000)
      : null

  return (
    <section className="card report">
      <div className="row between">
        <h2>{data.title}</h2>
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          onClick={() => void queryClient.invalidateQueries({ queryKey: ['admin-report', eventId] })}
        >
          Refresh
        </button>
      </div>

      <div className="stat-grid">
        <Stat label="Seats sold" value={`${data.bookedSeats} / ${data.totalSeats}`} />
        <Stat label="Revenue" value={formatMoney(data.revenuePaise)} />
        <Stat label="Confirmed bookings" value={String(data.confirmedBookings)} />
        <Stat label="Successful payments" value={String(data.successfulPayments)} />
        <Stat label="Tickets issued" value={String(data.ticketsIssued)} />
        <Stat label="Checked in" value={String(data.ticketsCheckedIn)} />
      </div>

      <h3>Sales by section</h3>
      <table className="table">
        <thead>
          <tr>
            <th>Section</th>
            <th className="right">Price</th>
            <th className="right">Sold</th>
            <th className="right">Available</th>
            <th className="right">Revenue</th>
          </tr>
        </thead>
        <tbody>
          {data.sections.map((section) => (
            <tr key={section.sectionId}>
              <td>{section.sectionName}</td>
              <td className="right">{formatMoney(section.pricePaise)}</td>
              <td className="right">{section.bookedSeats}</td>
              <td className="right">{section.totalSeats - section.bookedSeats}</td>
              <td className="right">{formatMoney(section.revenuePaise)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h3>Fairness</h3>
      <div className="stat-grid">
        <Stat label="Peak queue" value={String(data.fairness.peakQueueLength)} />
        <Stat label="Joined the queue" value={String(data.fairness.totalJoined)} />
        <Stat label="Admitted" value={String(data.fairness.totalAdmitted)} />
        <Stat label="Abandoned" value={String(data.fairness.totalAbandoned)} />
        <Stat
          label="Average wait"
          value={data.fairness.totalAdmitted > 0 ? formatWait(data.fairness.avgWaitSeconds) : '—'}
        />
        <Stat label="Still waiting" value={String(data.fairness.currentQueueLength)} />
        <Stat label="Time to sell out" value={timeToSellOut === null ? 'Not yet' : formatWait(timeToSellOut)} />
      </div>
    </section>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="stat">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}
