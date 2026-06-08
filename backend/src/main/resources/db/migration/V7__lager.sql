create table inventory_count (
    id      uuid        primary key,
    ts      timestamp with time zone not null,
    by_name varchar(120) not null,
    notes   varchar(2000)
);

create table inventory_count_line (
    id                  uuid        primary key,
    inventory_count_id  uuid        not null references inventory_count (id) on delete cascade,
    product_id          uuid        references product (id) on delete set null,
    product_name        varchar(120) not null,
    counted_qty         int         not null check (counted_qty >= 0),
    expected_qty        int         not null,
    diff_qty            int         not null
);
create index idx_inventory_count_line_count   on inventory_count_line (inventory_count_id);
create index idx_inventory_count_line_product on inventory_count_line (product_id);

create table stock_intake (
    id      uuid        primary key,
    ts      timestamp with time zone not null,
    by_name varchar(120) not null,
    notes   varchar(2000)
);

create table stock_intake_line (
    id               uuid        primary key,
    stock_intake_id  uuid        not null references stock_intake (id) on delete cascade,
    product_id       uuid        references product (id) on delete set null,
    product_name     varchar(120) not null,
    qty              int         not null check (qty > 0)
);
create index idx_stock_intake_line_intake  on stock_intake_line (stock_intake_id);
create index idx_stock_intake_line_product on stock_intake_line (product_id);
