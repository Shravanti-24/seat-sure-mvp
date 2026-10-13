import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/endpoints'
import { ErrorBanner } from '../components/ErrorBanner'
import { Spinner } from '../components/Spinner'
import { formatDateTime, formatTime } from '../lib/format'
import type { Ticket } from '../api/types'

export function MyTicketsPage() {
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ['my-tickets'],
    queryFn: api.myTickets,
  })

  if (isPending) {
    return <Spinner label="Loading your tickets" />
  }
  if (error) {
    return <ErrorBanner error={error} onRetry={refetch} />
  }
  if (!data || data.length === 0) {
    return (
      <div className="card empty">
        <h2>No tickets yet</h2>
        <p className="muted">Once you have paid for a seat, its QR code appears here.</p>
        <Link to="/" className="btn btn-primary btn-sm">
          Browse events
        </Link>
      </div>
    )
  }

  return (
    <>
      <div className="page-head">
        <h1>My tickets</h1>
        <p className="muted">Show the QR code at the gate. Each one can be scanned exactly once.</p>
      </div>
      <ul className="ticket-grid">
        {data.map((ticket) => (
          <TicketCard key={ticket.id} ticket={ticket} />
        ))}
      </ul>
    </>
  )
}

function TicketCard({ ticket }: { ticket: Ticket }) {
  const [qrUrl, setQrUrl] = useState<string | null>(null)
  const [qrError, setQrError] = useState<unknown>(null)

  // The PNG needs the Authorization header, so it is fetched as a blob rather than put in an <img src>.
  useEffect(() => {
    let objectUrl: string | null = null
    api
      .ticketQrUrl(ticket.id)
      .then((url) => {
        objectUrl = url
        setQrUrl(url)
      })
      .catch(setQrError)
    return () => {
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl)
      }
    }
  }, [ticket.id])

  const used = ticket.checkedInAt !== null

  return (
    <li className={used ? 'card ticket-card ticket-used' : 'card ticket-card'}>
      <div className="ticket-head">
        <h2>{ticket.eventTitle}</h2>
        {used ? <span className="badge badge-expired">Used</span> : <span className="badge badge-confirmed">Valid</span>}
      </div>
      <p className="muted small">
        {formatDateTime(ticket.eventStartsAt)} · {ticket.venueName}, {ticket.venueCity}
      </p>
      <div className="ticket-seat">
        <span>{ticket.sectionName}</span>
        <strong>{ticket.seatLabel}</strong>
      </div>
      <div className="qr">
        {qrError ? (
          <ErrorBanner error={qrError} />
        ) : qrUrl ? (
          <img src={qrUrl} alt={`QR code for seat ${ticket.seatLabel}`} />
        ) : (
          <Spinner />
        )}
      </div>
      {used ? (
        <p className="muted small">Checked in at {ticket.checkedInAt ? formatTime(ticket.checkedInAt) : '—'}</p>
      ) : (
        <p className="muted small">Ticket #{ticket.id} · booking #{ticket.bookingId}</p>
      )}
    </li>
  )
}
