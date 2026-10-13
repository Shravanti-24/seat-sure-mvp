-- Only applied under spring.profiles.active=loadtest.
--
-- 500 attendee accounts that all share one password, plus a 50-seat event. The point of the load
-- test is to put 500 users on far too few seats and then prove in SQL that not one seat was sold
-- twice. The event is a normal sale, not a high-demand one, so the k6 script measures the booking
-- path itself rather than the waiting room.

insert into venue (name, city) values ('Load Test Arena', 'Bengaluru');

insert into venue_section (venue_id, name, row_count, seats_per_row)
select id, 'Floor', 5, 10 from venue where name = 'Load Test Arena';

insert into seat (section_id, row_label, seat_number)
select s.id, chr(65 + r.row_index), n.seat_number
from venue_section s
         join generate_series(0, 4) as r(row_index) on true
         join generate_series(1, 10) as n(seat_number) on true
where s.name = 'Floor'
  and s.venue_id = (select id from venue where name = 'Load Test Arena')
order by r.row_index, n.seat_number;

insert into event (venue_id, title, description, starts_at, sale_opens_at, high_demand, max_seats_per_user, status)
select id,
       'Load Test Drop (50 seats)',
       'Seeded by the loadtest profile. 500 virtual users race for 50 seats.',
       now() + interval '7 days',
       now() - interval '1 hour',
       false,
       6,
       'ON_SALE'
from venue
where name = 'Load Test Arena';

insert into event_section_price (event_id, section_id, price_paise)
select e.id, s.id, 100000
from event e
         join venue v on v.id = e.venue_id
         join venue_section s on s.venue_id = v.id
where v.name = 'Load Test Arena';

insert into event_seat (event_id, seat_id, status)
select e.id, s.id, 'AVAILABLE'
from event e
         join venue v on v.id = e.venue_id
         join venue_section vs on vs.venue_id = v.id
         join seat s on s.section_id = vs.id
where v.name = 'Load Test Arena';

insert into users (name, email, password_hash, role)
select 'Load Test User ' || i,
       'user' || i || '@test.dev',
       '${loadtestPasswordHash}',
       'ATTENDEE'
from generate_series(1, 500) as g(i);
