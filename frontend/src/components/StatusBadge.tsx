import type { BookingStatus, EventStatus } from '../api/types'

const EVENT_LABELS: Record<EventStatus, string> = {
  DRAFT: 'Draft',
  ON_SALE: 'On sale',
  SOLD_OUT: 'Sold out',
  CLOSED: 'Closed',
}

const BOOKING_LABELS: Record<BookingStatus, string> = {
  PENDING_PAYMENT: 'Awaiting payment',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
}

export function EventStatusBadge({ status }: { status: EventStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{EVENT_LABELS[status]}</span>
}

export function BookingStatusBadge({ status }: { status: BookingStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{BOOKING_LABELS[status]}</span>}
