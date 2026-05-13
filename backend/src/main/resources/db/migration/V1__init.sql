-- Schulkasse · Kuchenverkauf — initial schema + seed

create table category (
    id          uuid        primary key,
    name        varchar(80) not null,
    color       varchar(20) not null,
    sort_order  int         not null default 0
);

create table product (
    id          uuid        primary key,
    category_id uuid        not null references category (id) on delete cascade,
    name        varchar(120) not null,
    price_cents int         not null check (price_cents >= 0),
    color       varchar(20) not null,
    sort_order  int         not null default 0
);
create index idx_product_category on product (category_id);

create table shift (
    id                   uuid        primary key,
    subject_key          varchar(200) not null,
    user_name            varchar(120) not null,
    role                 varchar(20)  not null,
    klasse               varchar(40),
    opening_cash_cents   int         not null default 0,
    started_at           timestamp with time zone not null,
    closed_at            timestamp with time zone,
    counted_cash_cents   int,
    expected_cash_cents  int,
    diff_cents           int,
    cash_sales_cents     int,
    card_sales_cents     int,
    total_sales_cents    int,
    sales_count          int,
    items_sold           int,
    notes                varchar(2000)
);
create index idx_shift_subject on shift (subject_key);
create index idx_shift_closed on shift (closed_at);

create table sale (
    id           uuid        primary key,
    shift_id     uuid        not null references shift (id) on delete cascade,
    ts           timestamp with time zone not null,
    method       varchar(10) not null check (method in ('BAR', 'KARTE')),
    total_cents  int         not null check (total_cents >= 0),
    given_cents  int         not null default 0,
    change_cents int         not null default 0,
    by_name      varchar(120) not null
);
create index idx_sale_shift on sale (shift_id);

create table sale_item (
    id          uuid        primary key,
    sale_id     uuid        not null references sale (id) on delete cascade,
    product_id  uuid        references product (id) on delete set null,
    name        varchar(120) not null,
    price_cents int         not null check (price_cents >= 0),
    qty         int         not null check (qty > 0),
    color       varchar(20) not null
);
create index idx_sale_item_sale on sale_item (sale_id);

create table user_pref (
    subject_key  varchar(200) primary key,
    theme        varchar(20)  not null default 'default',
    updated_at   timestamp with time zone  not null default now()
);

-- ----------------------------------------------------------------------
-- Seed: DEFAULT_CATS from the prototype (design/project/js/data.js)
-- ----------------------------------------------------------------------
insert into category (id, name, color, sort_order) values
    ('00000000-0000-0000-0000-000000000001', 'Kuchen',           'peach', 1),
    ('00000000-0000-0000-0000-000000000002', 'Muffins & Kekse',  'pink',  2),
    ('00000000-0000-0000-0000-000000000003', 'Herzhaft',         'mint',  3),
    ('00000000-0000-0000-0000-000000000004', 'Getränke',         'blue',  4);

insert into product (id, category_id, name, price_cents, color, sort_order) values
    -- Kuchen
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 'Schokokuchen',    150, 'peach',  1),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000001', 'Apfelkuchen',     150, 'yellow', 2),
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000001', 'Käsekuchen',      180, 'yellow', 3),
    ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000001', 'Marmorkuchen',    120, 'peach',  4),
    ('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000001', 'Rührkuchen',      100, 'mint',   5),
    ('00000000-0000-0000-0000-000000000106', '00000000-0000-0000-0000-000000000001', 'Zitronenkuchen',  150, 'yellow', 6),
    -- Muffins & Kekse
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000002', 'Schoko-Muffin',    100, 'pink',  1),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000002', 'Beeren-Muffin',    100, 'pink',  2),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000002', 'Cookies (3 Stk.)', 150, 'peach', 3),
    ('00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000002', 'Brownies',         120, 'peach', 4),
    ('00000000-0000-0000-0000-000000000205', '00000000-0000-0000-0000-000000000002', 'Cake Pop',          80, 'pink',  5),
    -- Herzhaft
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000003', 'Pizzaschnecke', 150, 'peach',  1),
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000003', 'Käsestange',    100, 'yellow', 2),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000003', 'Brezel',         80, 'yellow', 3),
    ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000003', 'Mini-Quiche',   180, 'mint',   4),
    -- Getränke
    ('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000004', 'Wasser 0,5L',   100, 'blue',     1),
    ('00000000-0000-0000-0000-000000000402', '00000000-0000-0000-0000-000000000004', 'Apfelschorle',  120, 'mint',     2),
    ('00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-000000000004', 'Eistee',        120, 'lavender', 3),
    ('00000000-0000-0000-0000-000000000404', '00000000-0000-0000-0000-000000000004', 'Kakao',         150, 'peach',    4);
