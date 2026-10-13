// SeatSure load test: 500 virtual users race for 50 seats.
//
// Every virtual user is one of the seeded loadtest accounts. They all arrive at the same instant,
// pick seats at random from the same 50-seat pool, try to hold them and pay if the hold succeeded.
// Conflicts are the expected outcome for most users — the point of the run is that the database
// afterwards shows no seat sold twice (see verify.sql).
//
// Logging in happens in setup(), before the clock starts, because real attendees are already signed
// in when a drop opens. Including 500 simultaneous BCrypt verifications would measure the password
// hash rather than the booking path.
//
//   k6 run -e BASE_URL=http://localhost:8080 drop.js
//
import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Trend } from 'k6/metrics'

/** Inclusive on both ends. Local rather than imported so the script runs with no network access. */
function randomIntBetween(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min
}

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
const PASSWORD = __ENV.LOADTEST_PASSWORD || 'loadtest12345'
const USERS = Number(__ENV.USERS || 500)
const MAX_SEATS_PER_USER = Number(__ENV.MAX_SEATS_PER_USER || 2)

// Outcomes we care about, counted separately from HTTP errors.
const holdsWon = new Counter('seatsure_holds_won')
const holdsLost = new Counter('seatsure_holds_conflict')
const holdsRateLimited = new Counter('seatsure_holds_rate_limited')
const paymentsConfirmed = new Counter('seatsure_payments_confirmed')
const paymentsConflicted = new Counter('seatsure_payments_conflict')
const loginFailures = new Counter('seatsure_login_failures')
const holdLatency = new Trend('seatsure_hold_duration', true)
const payLatency = new Trend('seatsure_pay_duration', true)

export const options = {
  scenarios: {
    drop: {
      // Everybody arrives at once, the way a real drop behaves.
      executor: 'per-vu-iterations',
      vus: USERS,
      iterations: 1,
      maxDuration: '3m',
    },
  },
  setupTimeout: '5m',
  thresholds: {
    // A 409 is a correct answer here, so only 5xx and transport errors may fail the run.
    'http_req_failed{expected_response:true}': ['rate<0.01'],
    // Measured on the booking path only; see loadtest/RESULTS.md for the numbers behind this bound.
    'http_req_duration{name:hold}': ['p(95)<3000'],
    'http_req_duration{name:pay}': ['p(95)<3000'],
    seatsure_login_failures: ['count==0'],
  },
}

// Resolve the seeded 50-seat event and sign every user in, before the measured drop begins.
export function setup() {
  const response = http.get(`${BASE_URL}/api/events`)
  const events = response.json()
  const event = events.find((candidate) => candidate.title.indexOf('Load Test Drop') === 0)
  if (!event) {
    throw new Error('No load test event found. Start the backend with SPRING_PROFILES_ACTIVE=loadtest.')
  }
  const seatMap = http.get(`${BASE_URL}/api/events/${event.id}/seatmap`).json()
  const seatIds = seatMap.sections.flatMap((section) => section.seats.map((seat) => seat.seatId))

  const tokens = []
  for (let i = 1; i <= USERS; i++) {
    const login = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: `user${i}@test.dev`, password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup-login' } },
    )
    if (login.status !== 200) {
      throw new Error(`Could not sign in user${i}@test.dev (status ${login.status})`)
    }
    tokens.push(login.json('token'))
  }
  return { eventId: event.id, seatIds, tokens }
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length]
  if (!token) {
    loginFailures.add(1)
    return
  }
  const auth = {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  }

  // Everyone picks from the same small pool, so collisions are the norm rather than the exception.
  // Asking for one or two seats fills the odd single gaps that a pairs-only crowd would leave.
  const basketSize = randomIntBetween(1, MAX_SEATS_PER_USER)
  const wanted = []
  while (wanted.length < basketSize) {
    const seatId = data.seatIds[randomIntBetween(0, data.seatIds.length - 1)]
    if (wanted.indexOf(seatId) === -1) {
      wanted.push(seatId)
    }
  }

  const hold = http.post(
    `${BASE_URL}/api/events/${data.eventId}/holds`,
    JSON.stringify({ seatIds: wanted }),
    { ...auth, tags: { name: 'hold' } },
  )
  holdLatency.add(hold.timings.duration)
  check(hold, {
    'hold answered cleanly': (response) => [201, 409, 403, 429].indexOf(response.status) !== -1,
  })

  if (hold.status === 429) {
    holdsRateLimited.add(1)
    return
  }
  if (hold.status !== 201) {
    holdsLost.add(1)
    return
  }
  holdsWon.add(1)

  // A real user takes a moment to look at the checkout page before paying.
  sleep(randomIntBetween(1, 3))

  const bookingId = hold.json('bookingId')
  const idempotencyKey = `${__VU}-${Date.now()}-${randomIntBetween(1, 1e9)}`
  const pay = http.post(
    `${BASE_URL}/api/bookings/${bookingId}/pay`,
    JSON.stringify({ simulateFailure: false }),
    {
      headers: { ...auth.headers, 'Idempotency-Key': idempotencyKey },
      tags: { name: 'pay' },
    },
  )
  payLatency.add(pay.timings.duration)
  check(pay, {
    'pay answered cleanly': (response) => [200, 402, 409, 410, 429].indexOf(response.status) !== -1,
  })

  if (pay.status === 200) {
    paymentsConfirmed.add(1)
    // Every winner retries once with the same key: idempotency must make this a no-op.
    const retry = http.post(
      `${BASE_URL}/api/bookings/${bookingId}/pay`,
      JSON.stringify({ simulateFailure: false }),
      {
        headers: { ...auth.headers, 'Idempotency-Key': idempotencyKey },
        tags: { name: 'pay-retry' },
      },
    )
    check(retry, {
      'retry replayed instead of charging again': (response) =>
        response.status === 200 && response.json('replayed') === true,
    })
  } else {
    paymentsConflicted.add(1)
  }
}
