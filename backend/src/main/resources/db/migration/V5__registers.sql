-- Multiple cash drawers ("Kassetten") per class: cashiers pick a register after
-- login, and each (class, register) combination runs its own independent shift.
create table register (
    id          uuid         primary key,
    name        varchar(80)  not null,
    sort_order  int          not null default 0,
    active      boolean      not null default true
);

-- Seed one default register so existing shifts/devices keep working without admin setup.
insert into register (id, name, sort_order, active) values
    ('00000000-0000-0000-0000-000000000501', 'Kassette 1', 1, true);

alter table shift add column register_id uuid references register (id);
alter table shift add column register_name varchar(80);

update shift set register_id = '00000000-0000-0000-0000-000000000501',
                 register_name = 'Kassette 1'
where register_id is null;

create index idx_shift_register on shift (register_id);

-- A given subject (class) may have at most one OPEN shift per register.
create unique index uq_shift_open_per_subject_register
    on shift (subject_key, register_id)
    where closed_at is null;
