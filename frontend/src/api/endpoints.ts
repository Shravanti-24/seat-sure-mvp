import { del, get, getBlobUrl, getPublic, post, put } from './client'
import type {
  AuthResponse,
  Booking,
  CheckInResult,
  EventDetail,
  EventReport,
  EventSummary,
  PayResponse,
  QueueJoin,
  QueueStatus,
  SeatMap,
  Ticket,
  User,
  Venue,
} from './types'

export const api = {
  // --- auth ---
  register: (name: string, email: string, password: string) =>
    post<AuthResponse>('/api/auth/register', { name, email, password }),
  login: (email: string, password: string) => post<AuthResponse>('/api/auth/login', { email, password }),
  me: () => get<User>('/api/auth/me'),

  // --- public catalogue ---
  events: () => getPublic<EventSummary[]>('/api/events'),
  event: (eventId: number) => getPublic<EventDetail>(`/api/events/${eventId}`),
  seatMap: (eventId: number) => getPublic<SeatMap>(`/api/events/${eventId}/seatmap`),

  // --- waiting room ---
  joinQueue: (eventId: number) => post<QueueJoin>(`/api/events/${eventId}/queue/join`),
  queueStatus: (eventId: number) => get<QueueStatus>(`/api/events/${eventId}/queue/status`),

  // --- booking ---
  hold: (eventId: number, seatIds: number[], admissionToken: string | null) =>
    post<Booking>(
      `/api/events/${eventId}/holds`,
      { seatIds },
      admissionToken ? { 'X-Admission-Token': admissionToken } : undefined,
    ),
  releaseHold: (bookingId: number) => del(`/api/holds/${bookingId}`),
  booking: (bookingId: number) => get<Booking>(`/api/bookings/${bookingId}`),
  myBookings: () => get<Booking[]>('/api/me/bookings'),

  /** The idempotency key is generated once per checkout attempt and reused for every retry. */
  pay: (bookingId: number, idempotencyKey: string, simulateFailure: boolean) =>
    post<PayResponse>(`/api/bookings/${bookingId}/pay`, { simulateFailure }, {
      'Idempotency-Key': idempotencyKey,
    }),

  // --- tickets ---
  myTickets: () => get<Ticket[]>('/api/me/tickets'),
  ticketQrUrl: (ticketId: number) => getBlobUrl(`/api/tickets/${ticketId}/qr`),

  // --- gate ---
  checkIn: (qrContent: string) => post<CheckInResult>('/api/checkin', { qrContent }),

  // --- admin ---
  adminVenues: () => get<Venue[]>('/api/admin/venues'),
  adminCreateVenue: (body: {
    name: string
    city: string
    sections: { name: string; rowCount: number; seatsPerRow: number }[]
  }) => post<Venue>('/api/admin/venues', body),
  adminEvents: () => get<EventSummary[]>('/api/admin/events'),
  adminCreateEvent: (body: {
    venueId: number
    title: string
    description: string
    startsAt: string
    saleOpensAt: string
    highDemand: boolean
    maxSeatsPerUser: number
    status: string
    prices: { sectionId: number; pricePaise: number }[]
  }) => post<EventDetail>('/api/admin/events', body),
  adminUpdateEvent: (eventId: number, body: Record<string, unknown>) =>
    put<EventDetail>(`/api/admin/events/${eventId}`, body),
  adminReport: (eventId: number) => get<EventReport>(`/api/admin/events/${eventId}/report`),
}
