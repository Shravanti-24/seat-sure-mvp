-- Run after the k6 drop against the loadtest database:
--   psql "$DATABASE_URL" -f loadtest/verify.sql
--
-- Every row must report PASS. These are the claims the load test exists to prove.

\echo '=== 1. No event seat belongs to more than one confirmed booking ==='
select case when count(*) = 0 then 'PASS' else 'FAIL' end as result,
       count(*) as seats_sold_twice
from (
    select bs.event_seat_id
    from booking_seat bs
             join booking b on b.id = bs.booking_id
    where b.status = 'CONFIRMED'
    group by bs.event_seat_id
    having count(*) > 1
) as duplicates;

\echo '=== 2. No event seat is BOOKED without exactly one confirmed booking row ==='
select case when count(*) = 0 then 'PASS' else 'FAIL' end as result,
       count(*) as inconsistent_seats
from event_seat es
where es.status = 'BOOKED'
  and (select count(*) from booking_seat bs where bs.event_seat_id = es.id and bs.confirmed) <> 1;

\echo '=== 3. Confirmed booked seats never exceed the 50 seats on sale ==='
select case when sold <= capacity then 'PASS' else 'FAIL' end as result,
       sold,
       capacity
from (
    select (select count(*) from event_seat es
            join event e on e.id = es.event_id
            where e.title like 'Load Test Drop%' and es.status = 'BOOKED') as sold,
           (select count(*) from event_seat es
            join event e on e.id = es.event_id
            where e.title like 'Load Test Drop%') as capacity
) as totals;

\echo '=== 4. Every confirmed booking has exactly one successful payment ==='
select case when count(*) = 0 then 'PASS' else 'FAIL' end as result,
       count(*) as bookings_with_wrong_payment_count
from booking b
where b.status = 'CONFIRMED'
  and (select count(*) from payment p where p.booking_id = b.id and p.status = 'SUCCESS') <> 1;

\echo '=== 5. Every idempotency key was used at most once (enforced by a unique index) ==='
select case when count(*) = 0 then 'PASS' else 'FAIL' end as result,
       count(*) as duplicate_keys
from (
    select idempotency_key from payment group by idempotency_key having count(*) > 1
) as duplicates;

\echo '=== 6. One ticket per confirmed seat ==='
select case when count(*) = 0 then 'PASS' else 'FAIL' end as result,
       count(*) as bookings_with_wrong_ticket_count
from booking b
where b.status = 'CONFIRMED'
  and (select count(*) from ticket t where t.booking_id = b.id)
   <> (select count(*) from booking_seat bs where bs.booking_id = b.id and bs.confirmed);

\echo '=== 7. Nobody exceeded the per-event seat limit ==='
select case when count(*) = 0 then 'PASS' else 'FAIL' end as result,
       count(*) as users_over_limit
from (
    select b.user_id, b.event_id, count(*) as seats
    from booking_seat bs
             join booking b on b.id = bs.booking_id
             join event e on e.id = b.event_id
    where b.status in ('PENDING_PAYMENT', 'CONFIRMED')
    group by b.user_id, b.event_id, e.max_seats_per_user
    having count(*) > max(e.max_seats_per_user)
) as over_limit;

\echo '=== Summary ==='
select e.title,
       count(*) filter (where es.status = 'BOOKED') as booked_seats,
       count(*) filter (where es.status = 'AVAILABLE') as available_seats,
       (select count(*) from booking b where b.event_id = e.id and b.status = 'CONFIRMED') as confirmed_bookings,
       (select count(*) from payment p join booking b on b.id = p.booking_id
        where b.event_id = e.id and p.status = 'SUCCESS') as successful_payments,
       (select count(*) from ticket t join booking b on b.id = t.booking_id
        where b.event_id = e.id) as tickets
from event e
         join event_seat es on es.event_id = e.id
where e.title like 'Load Test Drop%'
group by e.id, e.title;
