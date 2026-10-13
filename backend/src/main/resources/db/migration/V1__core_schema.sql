-- SeatSure core schema.
--
-- Two deliberate choices worth noting:
--  * money is stored as integer paise, never a floating point type;
--  * there is no "held" state anywhere. A hold is a Redis key with a TTL, so an abandoned
--    checkout cannot leave a seat stuck in the database.

create table users (
    id            bigserial primary key,
    name          varchar(120) not null,
    email         varchar(190) not null unique,
    password_hash varchar(100) not null,
    role          varchar(20)  not null,
    created_at    timestamptz  not null default now()
);

create table venue (
    id         bigserial primary key,
    name       varchar(160) not null,
    city       varchar(120) not null,
    created_at timestamptz  not null default now()
);

create table venue_section (
    id            bigserial primary key,
    venue_id      bigint      not null references venue (id),
    name          varchar(80) not null,
    row_count     int         not null check (row_count > 0),
    seats_per_row int         not null check (seats_per_row > 0),
    unique (venue_id, name)
);

create table seat (
    id          bigserial primary key,
    section_id  bigint     not null references venue_section (id),
    row_label   varchar(4) not null,
    seat_number int        not null check (seat_number > 0),
    unique (section_id, row_label, seat_number)
);

create table event (
    id                 bigserial primary key,
    venue_id           bigint       not null references venue (id),
    title              varchar(200) not null,
    description        text,
    starts_at          timestamptz  not null,
    sale_opens_at      timestamptz  not null,
    high_demand        boolean      not null default false,
    max_seats_per_user int          not null default 6 check (max_seats_per_user > 0),
    status             varchar(20)  not null check (status in ('DRAFT', 'ON_SALE', 'SOLD_OUT', 'CLOSED')),
    created_at         timestamptz  not null default now()
);

create table event_section_price (
    event_id    bigint not null references event (id),
    section_id  bigint not null references venue_section (id),
    price_paise bigint not null check (price_paise >= 0),
    primary key (event_id, section_id)
);

create table booking (
    id           bigserial primary key,
    user_id      bigint      not null references users (id),
    event_id     bigint      not null references event (id),
    status       varchar(20) not null check (status in ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    total_paise  bigint      not null check (total_paise >= 0),
    created_at   timestamptz not null default now(),
    confirmed_at timestamptz
);

-- version is the optimistic lock. Two confirmations that read the same row cannot both write it:
-- the second UPDATE matches zero rows and Hibernate raises an OptimisticLockException.
create table event_seat (
    id         bigserial primary key,
    event_id   bigint      not null references event (id),
    seat_id    bigint      not null references seat (id),
    status     varchar(16) not null check (status in ('AVAILABLE', 'BOOKED')),
    booking_id bigint references booking (id),
    version    bigint      not null default 0,
    unique (event_id, seat_id)
);

-- Rows are written when the hold is taken and flipped to confirmed inside the payment transaction.
-- The partial unique index is the second, independent guarantee against a double booking: a seat
-- can appear in at most one *confirmed* booking, whatever the application code does.
create table booking_seat (
    booking_id    bigint  not null references booking (id),
    event_seat_id bigint  not null references event_seat (id),
    confirmed     boolean not null default false,
    primary key (booking_id, event_seat_id)
);

create unique index uq_booking_seat_confirmed on booking_seat (event_seat_id) where confirmed;

-- The unique index on idempotency_key is what makes paying idempotent.
create table payment (
    id              bigserial primary key,
    booking_id      bigint      not null references booking (id),
    idempotency_key varchar(64) not null unique,
    amount_paise    bigint      not null check (amount_paise >= 0),
    status          varchar(16) not null check (status in ('SUCCESS', 'FAILED')),
    gateway_ref     varchar(64),
    failure_reason  varchar(200),
    created_at      timestamptz not null default now()
);

create table ticket (
    id            bigserial primary key,
    booking_id    bigint      not null references booking (id),
    event_seat_id bigint      not null references event_seat (id),
    token         varchar(36) not null unique,
    checked_in_at timestamptz,
    checked_in_by bigint references users (id)
);

create table queue_stats (
    event_id          bigint primary key references event (id),
    peak_queue_length int              not null default 0,
    total_joined      int              not null default 0,
    total_admitted    int              not null default 0,
    total_abandoned   int              not null default 0,
    avg_wait_seconds  double precision not null default 0,
    sold_out_at       timestamptz
);

create index idx_event_seat_event_status on event_seat (event_id, status);
create index idx_booking_user_event on booking (user_id, event_id);
create index idx_booking_status_created on booking (status, created_at);
create index idx_ticket_booking on ticket (booking_id);
create index idx_payment_booking on payment (booking_id);
