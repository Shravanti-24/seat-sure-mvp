// Mirrors the backend DTOs exactly. Nothing in the frontend uses `any`.

export type Role = 'ATTENDEE' | 'STAFF' | 'ADMIN'

export interface User {
  id: number
  name: string
  email: string
  role: Role
}

export interface AuthResponse {
  token: string
  expiresInSeconds: number
  user: User
}

export type EventStatus = 'DRAFT' | 'ON_SALE' | 'SOLD_OUT' | 'CLOSED'

export interface EventSummary {
  id: number
  title: string
  venueName: string
  venueCity: string
  startsAt: string
  saleOpensAt: string
  highDemand: boolean
  status: EventStatus
  maxSeatsPerUser: number
  minPricePaise: number | null
  maxPricePaise: number | null
  availableSeats: number
  totalSeats: number
}

export interface SectionPrice {
  sectionId: number
  sectionName: string
  pricePaise: number
}

export interface EventDetail {
  summary: EventSummary
  description: string | null
  prices: SectionPrice[]
}

export type SeatMapStatus = 'AVAILABLE' | 'HELD' | 'BOOKED'

export interface SeatMapSeat {
  seatId: number
  eventSeatId: number
  rowLabel: string
  seatNumber: number
  label: string
  status: SeatMapStatus
}

export interface SeatMapSection {
  sectionId: number
  sectionName: string
  pricePaise: number
  rowCount: number
  seatsPerRow: number
  seats: SeatMapSeat[]
}

export interface SeatMap {
  eventId: number
  status: EventStatus
  highDemand: boolean
  maxSeatsPerUser: number
  saleOpensAt: string
  sections: SeatMapSection[]
}

export type BookingStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED'

export interface HeldSeat {
  seatId: number
  eventSeatId: number
  sectionName: string
  label: string
  pricePaise: number
}

export interface Booking {
  bookingId: number
  eventId: number
  eventTitle: string
  venueName: string
  eventStartsAt: string
  status: BookingStatus
  totalPaise: number
  createdAt: string
  confirmedAt: string | null
  holdExpiresAt: string | null
  seats: HeldSeat[]
}

export type PaymentStatus = 'SUCCESS' | 'FAILED'

export interface PayResponse {
  paymentId: number
  bookingId: number
  status: PaymentStatus
  bookingStatus: BookingStatus
  amountPaise: number
  gatewayRef: string | null
  message: string
  ticketIds: number[]
  replayed: boolean
}

export interface QueueJoin {
  position: number
  estimatedWaitSeconds: number
  joinedAt: string
}

export interface QueueStatus {
  position: number
  queueLength: number
  estimatedWaitSeconds: number
  admitted: boolean
  admissionToken: string | null
  admissionExpiresAt: string | null
  saleOpen: boolean
  saleOpensAt: string
}

export interface Ticket {
  id: number
  bookingId: number
  eventId: number
  eventTitle: string
  venueName: string
  venueCity: string
  eventStartsAt: string
  sectionName: string
  seatLabel: string
  checkedInAt: string | null
}

export interface CheckInResult {
  result: 'OK' | 'ALREADY_USED'
  message: string
  attendeeName: string
  eventTitle: string
  sectionName: string
  seatLabel: string
  checkedInAt: string | null
}

export interface VenueSection {
  id: number
  name: string
  rowCount: number
  seatsPerRow: number
  seatCount: number
}

export interface Venue {
  id: number
  name: string
  city: string
  totalSeats: number
  sections: VenueSection[]
}

export interface QueueStats {
  peakQueueLength: number
  totalJoined: number
  totalAdmitted: number
  totalAbandoned: number
  avgWaitSeconds: number
  currentQueueLength: number
  soldOutAt: string | null
}

export interface SectionSales {
  sectionId: number
  sectionName: string
  pricePaise: number
  totalSeats: number
  bookedSeats: number
  revenuePaise: number
}

export interface EventReport {
  eventId: number
  title: string
  venueName: string
  startsAt: string
  saleOpensAt: string
  highDemand: boolean
  status: string
  totalSeats: number
  bookedSeats: number
  availableSeats: number
  revenuePaise: number
  confirmedBookings: number
  successfulPayments: number
  ticketsIssued: number
  ticketsCheckedIn: number
  sections: SectionSales[]
  fairness: QueueStats
}

/** The single error shape every endpoint returns. */
export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  details: number[]
}
