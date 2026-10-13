# Design decisions

Every entry is a choice I made while building SeatSure, the alternative I rejected, and why. This is
the file I revise before an interview.

---

## 1. Modular monolith, not microservices

**Chose:** one Spring Boot app with package-per-domain (`auth`, `venue`, `event`, `booking`,
`payment`, `ticket`, `queue`, `ratelimit`, `report`, `common`).
**Alternative:** separate booking / payment / queue services.
**Why:** the hard part of this problem is a *transaction* that must cover seats, payment and tickets
at once. In one database that is a single `@Transactional` method; split across services it becomes a
saga with compensating actions and a new failure mode for every hop. The package boundaries are real
(entities only cross them in one direction), so splitting later is mechanical if traffic ever demands
it. Nothing about fairness or correctness here is helped by a network boundary.

## 2. Seat holds live in Redis, not in a database column

**Chose:** a hold is the Redis key `hold:{eventId}:{seatId} = userId` with a 7-minute TTL. There is
deliberately no `HELD` value in the `event_seat.status` enum.
**Alternative:** a `held_by` / `held_until` column in Postgres, swept by a cron job.
**Why:** a column needs somebody to clean it up. If the app crashes between "set held" and "clear
held", the seat is stuck until a sweeper runs, and a sweeper that is late is indistinguishable from a
bug. A TTL cannot be late: Redis forgets the hold whether or not my code is alive. It is also the
hot path — thousands of people clicking the same seats — and `SET NX` is a single in-memory
operation rather than a row update that fights for locks with everyone else.
**Cost:** the seat map has to merge two sources (Postgres for `AVAILABLE`/`BOOKED`, Redis for
`HELD`), and Redis is now required for booking to work at all.

## 3. All-or-nothing holds via a Lua script

**Chose:** `backend/src/main/resources/redis/acquire_holds.lua` runs `SET NX EX` for each seat in one
server-side script. If any seat is taken it deletes the keys *this call* acquired and returns the
failed seats, so the client gets a 409 listing exactly which seats it lost.
**Alternative:** loop `SET NX` from Java; or `WATCH`/`MULTI` optimistic transactions.
**Why:** Redis runs a script atomically, so no other request can interleave between my individual
`SET NX` calls. A Java loop can half-succeed and then has to roll back over the network, during which
another user sees seats that are about to be released. `WATCH`/`MULTI` would need a retry loop on
every contended key, which under a drop is every key. The script also tracks which keys it acquired,
so a rollback never deletes a hold that legitimately belonged to someone else.

## 4. Redis holds *and* database optimistic locking: defence in depth

**Chose:** both. The hold stops the race almost always; `@Version` on `event_seat` stops it always.
**Alternative:** either one alone.
**Why:** they protect different things. Redis is fast and self-cleaning but it is a cache — it can be
flushed, fail over, or lose a key, and a hold that vanishes must never be able to sell a seat twice.
Postgres is the source of truth and its version check is exact, but using it alone would mean holding
a row lock (or re-reading and retrying) for the entire time a user is typing their card details.
So: Redis decides *who gets to try*, Postgres decides *who actually gets the seat*. The confirmation
re-reads each `event_seat` under `OPTIMISTIC_FORCE_INCREMENT`, flips it to `BOOKED`, and flushes
inside the payment transaction; the loser of a race gets an `OptimisticLockException` and a 409.
There is also a third net: a partial unique index on `booking_seat (event_seat_id) where confirmed`,
so even a bug in my own code cannot commit a seat into two confirmed bookings.

## 5. Why `booking_seat` has a `confirmed` flag

**Chose:** write `booking_seat` rows when the hold is taken, with `confirmed = false`, and flip them
inside the payment transaction. The unique index is partial: `where confirmed`.
**Alternative:** a plain `unique (event_seat_id)`, with rows only written at confirmation time.
**Why:** the spec wants a seat to be in at most one *confirmed* booking, and a pending booking needs
to know its seats so the checkout page can render them. A plain unique constraint would then reject
the second user's hold on a seat whose earlier hold had expired, which is wrong. The flag lets one
index express "at most one confirmed owner" while keeping the full history of attempts.

## 6. Money as integer paise

**Chose:** `price_paise`, `total_paise`, `amount_paise`, all `bigint`.
**Alternative:** `numeric(10,2)` rupees, or a `double`.
**Why:** `double` cannot represent 0.01 exactly, and summing six seat prices in floating point can
produce a total that is a paisa off from what the gateway charged. Integers make every arithmetic
operation exact, and formatting for display is a UI concern. `numeric` would also be exact, but then
JPA hands me `BigDecimal` everywhere and equality checks get subtle.

## 7. Idempotent payments keyed by a client-generated UUID

**Chose:** a required `Idempotency-Key` header, stored in `payment.idempotency_key` with a unique
index. If the key has been seen, the stored result is rebuilt and returned (`replayed: true`) without
calling the gateway or touching any seat.
**Alternative:** deduplicate on `booking_id` alone, or let the frontend disable the Pay button.
**Why:** the dangerous retry is the one I cannot see — a request that timed out after Postgres
committed. The client cannot know whether it succeeded, so it must be safe to send again, and only a
key chosen *before* the first attempt makes two requests recognisably the same attempt. A disabled
button protects nothing: it is gone on refresh and absent from a script. `booking_id` alone would be
wrong too, because a genuinely failed payment must be retryable with a new key. The unique index also
settles the simultaneous case: if two copies of the same request race, one insert wins and the other
catches the constraint violation and replays the winner's result.

## 8. The payment transaction is deliberately narrow

**Chose:** `PaymentService` is *not* transactional. The idempotency lookup, the gateway call and the
Redis cleanup happen outside; only `PaymentTransactionService.confirmAndRecord` is `@Transactional`,
covering payment row + seats + booking + tickets.
**Alternative:** wrap the whole endpoint in one transaction.
**Why:** a database transaction that waits on an external HTTP call holds its locks for the gateway's
latency, which is the one thing you cannot afford during a drop. Keeping the gateway call outside
means the transaction only spans local writes. The holds are released *after* commit, because
releasing them before would open a window where the seat looks free but is already sold.

## 9. Virtual waiting room as a Redis sorted set

**Chose:** `ZADD NX queue:{eventId} <join-time-ms> <userId>`; position is `ZRANK`; a scheduler pops
the front `N` with `ZPOPMIN` every 5 seconds and issues an admission token.
**Alternative:** a database table with a sequence; or no queue, first-request-wins.
**Why:** arrival order *is* the score, so correctness does not depend on polling frequency: `ZADD NX`
means a refresh, a second tab or a script hammering the endpoint cannot improve your place. Only the
scheduler removes anyone, so order is stable. `ZRANK` gives the live position in O(log n) which is
what makes "you are #312" cheap enough to poll. A table would need an index scan per poll and a
transaction per admission. First-request-wins is what SeatSure exists to avoid: it rewards whoever has
the fastest connection and the best bot.

## 10. Admission tokens, not a boolean

**Chose:** admission is the Redis key `admit:{eventId}:{userId} = <random UUID>` with a 10-minute TTL,
and the hold endpoint for a high-demand event requires that exact token in `X-Admission-Token`
(compared with `MessageDigest.isEqual`).
**Alternative:** mark the user admitted in the session/JWT, or just trust that they came from the
queue page.
**Why:** a boolean in a token cannot expire on the server, so one admission would be reusable
forever; and anything client-side can be forged. A server-side key with a TTL means admission is
*spendable* — you get a window, and when it lapses you queue again like everyone else. The constant
time comparison keeps the check free of a timing side channel.

## 11. Abandonment is detected by polling, not by a disconnect

**Chose:** every queue poll writes `lastSeen:{eventId}[userId] = now`. A user not seen for 60 seconds
is removed with `ZREM` and counted as abandoned.
**Alternative:** WebSockets, so a closed socket means a departure.
**Why:** polling makes the heartbeat the same request that already has to happen to show the
position, so there is nothing extra to build and nothing stateful between client and server. A
closed tab stops polling and drains out on its own. WebSockets would be more immediate and cheaper
per user at scale, but they add sticky connections and a reconnect protocol for a signal I only need
at 60-second resolution.

## 12. Anti-hoarding is counted in the database

**Chose:** `BookingSeatRepository.countSeatsForUser` sums the user's seats across `PENDING_PAYMENT`
and `CONFIRMED` bookings for the event and compares against `event.max_seats_per_user`.
**Alternative:** disable extra checkboxes in the UI, or count the user's Redis holds.
**Why:** the UI limit is advice to an honest client; a script posts straight to the API. Counting in
SQL means the limit is enforced where the data is. Pending bookings have to count, otherwise opening
six tabs gets you 36 seats. Counting Redis holds instead would need a key scan and would lose the
seats the user has already paid for.

## 13. Replay-proof check-in with one conditional UPDATE

**Chose:** `update ticket set checked_in_at = now, checked_in_by = ? where id = ? and checked_in_at
is null`, and the row count decides the answer.
**Alternative:** read the ticket, check `checked_in_at`, then write.
**Why:** read-then-write has a window: two gate scanners (or one double tap) can both read "not used"
and both write. Putting the condition in the `WHERE` clause makes the database the arbiter — exactly
one statement can match, so the second scan gets 0 rows and shows "Already used at 21:27:30". No
lock, no retry, no application-level coordination.

## 14. QR codes signed with HMAC-SHA256

**Chose:** QR content is `ticketId.token.signature` where the signature is HMAC-SHA256 of
`ticketId.token` under a server secret.
**Alternative:** put only the random token in the QR; or sign with a JWT.
**Why:** the random token alone would be fine for secrecy but makes the gate do a database lookup
before it can reject rubbish, and a leaked token list would be usable. With an HMAC the gate rejects a
forged or edited code using the secret alone, and nobody without the secret can mint a code for a
ticket id that does not exist yet. A JWT would work but carries a header, claims and a parser for a
payload of two fields — too much to fit comfortably in a QR that has to scan in bad light.

## 15. Stateless JWT with BCrypt passwords

**Chose:** HS256 JWT holding subject/user id/role, verified per request; the filter reloads the user
so a role change or deletion takes effect immediately.
**Alternative:** server-side sessions.
**Why:** sessions would need sticky routing or a shared session store, which is exactly the state I
do not want when the booking API is the thing that has to scale during a drop. The trade-off is that
a JWT cannot be revoked before it expires, which I limit with a 12-hour expiry; the per-request user
reload closes most of that gap at the cost of one indexed query.

## 16. Seeded operator accounts via Flyway, passwords from the environment

**Chose:** `V3__seed_staff_accounts.sql` inserts the admin and gate users with `${adminPasswordHash}`
placeholders. `SeedPasswordFlywayCustomizer` BCrypts `SEED_ADMIN_PASSWORD` / `SEED_STAFF_PASSWORD`
at startup and hands the hashes to Flyway.
**Alternative:** commit a hash, or seed from an `ApplicationRunner`.
**Why:** a committed hash is a committed credential — it survives in git history and is identical in
every deployment. This way the migration stays declarative and versioned, while the actual secret
only ever exists in the environment. (The encoder bean lives in its own `@Configuration` rather than
in `SecurityConfig`, because the Flyway customizer needs it before the security filter chain exists
and injecting it from there creates a bean cycle.)

## 17. Fixed-window rate limiting

**Chose:** `INCR rl:{userId}:{group}:{window}` with an `EXPIRE` on first use, applied by a
`HandlerInterceptor` driven by a `@RateLimited(group = "...")` annotation; 429 plus `Retry-After`.
**Alternative:** sliding window log, or a token bucket.
**Why:** one Redis round trip per request, no state to clean up, and the limit is declared next to the
endpoint it protects. The known flaw is burstiness at the boundary — up to 2× the limit across two
adjacent windows. For stopping a script from hammering the booking API that is acceptable; a token
bucket would smooth it out but needs a Lua script and per-user timestamps. Grouping endpoints means a
client cannot spread its load over several URLs to get a bigger total budget.

## 18. `@Transactional` methods always live on another bean

**Chose:** `PendingBookingService`, `PaymentTransactionService` and `PaymentReplayService` exist
mainly so that transactional work is invoked across a bean boundary.
**Alternative:** private/protected `@Transactional` methods on the calling service.
**Why:** Spring's transactions are proxy-based, so a `@Transactional` method called from inside the
same class runs with no transaction at all — silently. It compiles, it passes a happy-path test, and
it loses atomicity exactly when something fails. Separate beans make the proxy boundary explicit.

## 19. Scheduled jobs are tidy-up, never correctness

**Chose:** `HoldExpiryJob` only flips abandoned `PENDING_PAYMENT` bookings to `EXPIRED`; the seats
were already freed by the Redis TTL.
**Alternative:** rely on the job to release seats.
**Why:** if a job is load-bearing then a paused scheduler, a long GC pause or a crashed instance
becomes a correctness bug. Here a late job only means a booking row says `PENDING_PAYMENT` for a
while longer; no seat is withheld from sale. The job matters for the seat limit (an expired booking
must stop counting), which is a fairness question, not a double-booking one.

## 20. `OPTIMISTIC`, not `OPTIMISTIC_FORCE_INCREMENT`

**Chose:** the confirmation reads each `event_seat` with `@Lock(LockModeType.OPTIMISTIC)` and then
writes it.
**Alternative:** `OPTIMISTIC_FORCE_INCREMENT`, which I tried first.
**Why:** force-increment schedules a version bump of its own *in addition to* the entity's own
update. Because the confirmation always modifies the row, the two writes collided and
`DoubleBookingGuardsTest` showed *both* competing transactions failing instead of one. Plain
`OPTIMISTIC` is the right tool when the row is going to be modified anyway: the version read is
checked by the update's `WHERE` clause, so exactly one writer wins. Force-increment is for the case
where you need a version bump on a row you only *read* — not this one.

## 21. Integration tests on real Postgres and Redis

**Chose:** Testcontainers starts `postgres:16-alpine` and `redis:7-alpine`, shared statically across
test classes. If `SEATSURE_TEST_DATASOURCE_URL` and `SEATSURE_TEST_REDIS_URL` are set, those servers
are used instead and the public schema is dropped first so Flyway replays from scratch.
**Alternative:** H2 with an embedded Redis mock.
**Why:** nearly everything worth testing here is database- and Redis-specific — a partial unique
index, `@Version` behaviour on a real `UPDATE ... WHERE version = ?`, Lua script atomicity, key TTLs.
On H2 those tests would pass while proving nothing about production. The environment override exists
because Docker 29 raised its minimum API version above what the Testcontainers client negotiates, so
on such a machine the suite can still run against a local Postgres and Redis instead of being
skipped.

## 22. The JWT lives in memory, mirrored into localStorage

**Chose:** the token is held in a module-level variable in `src/api/client.ts` and written to
`localStorage` only so a page reload can restore the session, which then calls `/api/auth/me` to
rebuild the user.
**Alternative:** an httpOnly cookie, or memory only with no persistence.
**Why, and the trade-off I would say out loud:** anything readable by JavaScript is readable by an
XSS payload, so this storage is not XSS-proof — an httpOnly, SameSite cookie would be, because the
script could never read the token. I did not use one because the API is stateless and cross-origin
(Vercel frontend, Render backend), which makes cookies mean CSRF protection and a shared parent
domain. Memory only would be safest, but then every refresh signs the user out, which during a drop
is worse than the risk it removes. The mitigation is that React escapes interpolated content by
default and the app never uses `dangerouslySetInnerHTML`.

## 23. Admission tokens in sessionStorage, seat picks in component state

**Chose:** the admission token the waiting room hands out is kept in `sessionStorage`, scoped per
event; the user's selected seats are plain React state.
**Alternative:** keeping the token in memory only.
**Why:** the token has to survive the navigation from the queue page to the seat map and any reload
on the way, or an admitted user would be bounced back into the queue by their own browser. It is
only a convenience copy: the server holds the authoritative key with a ten-minute TTL, so a stale
copy in the tab buys nothing.

## 24. Polling, not WebSockets, for the seat map and the queue

**Chose:** TanStack Query with `refetchInterval` — 3 seconds for the seat map and the queue, 5 for
the events list and the organiser report.
**Alternative:** a WebSocket or SSE stream pushing seat changes.
**Why:** polling is stateless, so the API can scale horizontally without sticky sessions, and the
queue poll doubles as the heartbeat that detects abandonment. Three seconds is fast enough that a
seat turning amber feels live, and the rate limiter is configured well above that interval so an
honest client is never throttled. A push channel would be lighter per user at large scale and is the
obvious next step if the event list ever grew.

## 25. The gate scanner is code-split

**Chose:** `GateScannerPage` is loaded with `React.lazy`.
**Why:** `html5-qrcode` is about 340 kB of the bundle and only staff ever open the scanner. Splitting
it keeps the attendee bundle — the one that has to load on a phone the moment a drop starts — at
roughly 239 kB (75 kB gzipped).

## 26. The load test measures the drop, not the login

**Chose:** k6's `setup()` signs all 500 users in before the clock starts; the measured iteration is
hold-then-pay only.
**Alternative:** log in inside the iteration, which is what the first version did.
**Why:** that first run reported a p95 of 14.7 s, and the cause was 500 simultaneous BCrypt
verifications on four cores, not the booking path. BCrypt is deliberately expensive, so a thundering
herd of logins is a CPU wall that says nothing about seat contention. Moving logins out — which is
also more faithful, since real attendees are already signed in when a drop opens — took p95 from
14.7 s to 2.0 s on the same hardware. The numbers are in `loadtest/RESULTS.md`, including the first
run, because the mistake is the more interesting half of the story.

## 27. The load-test event is a normal sale

**Chose:** the seeded `Load Test Drop (50 seats)` event has `high_demand = false`, so k6 goes
straight at the hold endpoint.
**Alternative:** route the 500 users through the waiting room first.
**Why:** the question this test answers is "can the booking path sell a seat twice under contention?"
A queue in front of it would throttle arrivals to 25 every 5 seconds, which is exactly the contention
the test is trying to create. The waiting room is covered by `FullFlowIntegrationTest` instead.
