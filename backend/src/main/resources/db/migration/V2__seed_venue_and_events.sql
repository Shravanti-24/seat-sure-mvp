-- Demo data: one real venue with 450 seats and the three events the UI shows.
-- Row labels are generated A, B, C ... to match VenueService.rowLabel().

insert into venue (id, name, city) values (1, 'Main Auditorium', 'Bengaluru');

insert into venue_section (id, venue_id, name, row_count, seats_per_row) values
    (1, 1, 'Gold', 5, 20),
    (2, 1, 'Silver', 8, 25),
    (3, 1, 'Balcony', 6, 25);

insert into seat (section_id, row_label, seat_number)
select s.id, chr(65 + r.row_index), n.seat_number
from venue_section s
         join generate_series(0, 59) as r(row_index) on r.row_index < s.row_count
         join generate_series(1, 60) as n(seat_number) on n.seat_number <= s.seats_per_row
where s.venue_id = 1
order by s.id, r.row_index, n.seat_number;

-- TechFest is the drop demo: the sale opens shortly after the schema is created, so the countdown
-- and the waiting room can be shown end to end on a fresh database.
insert into event (id, venue_id, title, description, starts_at, sale_opens_at, high_demand, max_seats_per_user, status)
values (1, 1,
        'TechFest 2026: Finale Night',
        'The closing night of TechFest 2026. Expect a full house: this drop uses the virtual waiting room.',
        now() + interval '45 days',
        now() + interval '${highDemandSaleDelayMinutes} minutes',
        true, 6, 'ON_SALE'),
       (2, 1,
        'Startup Pitch Day',
        'Twelve student startups pitch to a panel of investors. Normal sale, no queue.',
        now() + interval '20 days',
        now() - interval '1 day',
        false, 6, 'ON_SALE'),
       (3, 1,
        'Alumni Music Evening',
        'An evening of live music from alumni bands. Normal sale, no queue.',
        now() + interval '60 days',
        now() - interval '1 day',
        false, 6, 'ON_SALE');

insert into event_section_price (event_id, section_id, price_paise) values
    (1, 1, 350000), (1, 2, 200000), (1, 3, 120000),
    (2, 1, 150000), (2, 2, 90000),  (2, 3, 50000),
    (3, 1, 250000), (3, 2, 150000), (3, 3, 80000);

insert into event_seat (event_id, seat_id, status)
select e.id, s.id, 'AVAILABLE'
from event e
         join seat s on true
         join venue_section vs on vs.id = s.section_id
where e.venue_id = vs.venue_id
order by e.id, s.id;

select setval('venue_id_seq', (select max(id) from venue));
select setval('venue_section_id_seq', (select max(id) from venue_section));
select setval('event_id_seq', (select max(id) from event));
