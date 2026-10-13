# Load test results

Every number on this page came out of an actual run. Nothing here is estimated.

## How to reproduce

```bash
# 1. a clean database and the loadtest profile (500 attendees + a 50-seat event)
createdb seatsure_load
SPRING_PROFILES_ACTIVE=loadtest \
  DATABASE_URL=jdbc:postgresql://localhost:5432/seatsure_load \
  DB_POOL_SIZE=40 \
  java -jar backend/target/seatsure-0.1.0.jar

# 2. the drop
k6 run -e BASE_URL=http://localhost:8080 --summary-export=out/summary.json loadtest/drop.js

# 3. prove it in SQL
psql -d seatsure_load -f loadtest/verify.sql
```

---

## Run of 13 October 2026

**Scenario.** 500 virtual users, one iteration each, all released at the same instant
(`per-vu-iterations`), against a 50-seat event. Each user asks for one or two seats drawn at random
from the same 50-seat pool, so almost everybody collides with somebody. Winners pause 1–3 s on the
checkout page, pay, and then deliberately retry the payment with the **same** `Idempotency-Key` to
prove the retry is a no-op.

**Where it ran.** Single machine, 4 vCPU / 15 GB RAM, Linux. Backend, PostgreSQL 16, Redis 7 and k6
all on that one box, so the latency figures include k6's own CPU cost and the database competing with
the JVM. On separate hosts the numbers would be better; they are reported as measured.

### Throughput and latency

| Metric | Value |
|---|---|
| Virtual users | 500 (1 iteration each) |
| Drop duration | **4.8 s** from first request to last |
| HTTP requests | 1,082 (19.9 req/s averaged over the run) |
| Iterations completed | 500 of 500, 0 interrupted |
| k6 checks | **580 passed, 0 failed** |
| `POST /holds` p95 | **2.02 s** (avg 1.50 s, median 1.61 s, max 2.08 s) |
| `POST /pay` p95 | **148 ms** (avg 80 ms, median 61 ms, max 166 ms) |
| All requests p95 | 1.99 s (avg 743 ms, median 107 ms) |
| 5xx / transport failures | **0** (`http_req_failed{expected_response:true}` = 0.00%) |

`http_req_failed` reads 42.51% overall, which looks alarming until you remember what it counts: k6
treats any 4xx as a failed request, and in this test a **409 is the correct answer** for the 460
users who asked for a seat somebody else got first. Filtering to responses that were meant to
succeed gives 0 failures out of 622.

### Outcomes

| Outcome | Count |
|---|---|
| Holds won | 40 |
| Holds lost to a conflict (409) | 460 |
| Holds rejected by the rate limiter (429) | 0 |
| Payments confirmed | 40 |
| Payments that lost a race (409/410) | 0 |
| Idempotent retries that replayed instead of charging | 40 of 40 |

The event sold out during the run: `status = SOLD_OUT`, `sold_out_at` recorded.

### Verification (`verify.sql`)

```
=== 1. No event seat belongs to more than one confirmed booking ===  PASS   seats_sold_twice = 0
=== 2. No event seat is BOOKED without exactly one confirmed booking row ===  PASS   inconsistent_seats = 0
=== 3. Confirmed booked seats never exceed the 50 seats on sale ===  PASS   sold = 50, capacity = 50
=== 4. Every confirmed booking has exactly one successful payment ===  PASS   bookings_with_wrong_payment_count = 0
=== 5. Every idempotency key was used at most once ===  PASS   duplicate_keys = 0
=== 6. One ticket per confirmed seat ===  PASS   bookings_with_wrong_ticket_count = 0
=== 7. Nobody exceeded the per-event seat limit ===  PASS   users_over_limit = 0

           title           | booked_seats | available_seats | confirmed_bookings | successful_payments | tickets
---------------------------+--------------+-----------------+--------------------+---------------------+---------
 Load Test Drop (50 seats) |           50 |               0 |                 40 |                  40 |      50
```

**50 seats, 50 sold, 0 sold twice.** 40 bookings covering 50 seats (users asked for one or two),
40 successful payments — one per booking — and 50 tickets, one per seat.

---

## What the first run taught me

The first version of the script had each virtual user log in inside the measured iteration. That run
reported a p95 of **14.7 s** and a maximum of 16.7 s, and I nearly wrote it up as the booking path
being slow. It was not: 500 simultaneous BCrypt verifications on 4 cores were the whole story.
BCrypt is *designed* to be expensive, so 500 of them at once is a CPU wall, and it had nothing to do
with holds, Redis or the database.

Moving the logins into k6's `setup()` — which is also more faithful, since real attendees are already
signed in when a drop opens — dropped p95 from 14.7 s to 2.0 s on identical hardware. The lesson I
would repeat in an interview: a load test measures whatever you put inside the timed section, so put
only the thing you are actually asking about in there.

The remaining 2 s p95 on `/holds` is the genuine cost of 500 concurrent requests on 4 shared cores:
each hold does a seat lookup, an anti-hoarding count, the Redis Lua script and a booking insert.
Payment, which does strictly more database work, sits at 148 ms p95 — because by then only 40 users
are left in the funnel. The shape of the result is the shape of the product: contention is absorbed
at the front door and the seats behind it are handed out calmly.

## What this run does not prove

- One machine, so these are not production capacity figures. They are a correctness proof with
  latency attached.
- The waiting room is not exercised here. The load-test event is a normal sale on purpose, so the
  measurement is of the booking path rather than of the queue. The queue has its own integration
  test (`FullFlowIntegrationTest`).
- Rate limiting never fired (0 × 429), because the loadtest profile raises the limits and each user
  makes one attempt. The limiter has its own unit tests and was verified by hand against a running
  server.
