-- PLU: admin-assigned, free-text, unique product code (optional during rollout).
alter table product add column plu varchar(40);
create unique index uq_product_plu on product (plu) where plu is not null;

-- Composition: a product can act as a "Verkaufstaste" that consumes N other
-- products in fixed quantities (e.g. "2-für-1 Red Bull" consumes Red Bull x2).
alter table product add column composed boolean not null default false;

create table product_component (
    id                    uuid primary key,
    parent_product_id     uuid not null references product (id) on delete cascade,
    component_product_id  uuid not null references product (id) on delete restrict,
    qty                   int  not null check (qty > 0),
    constraint uq_product_component unique (parent_product_id, component_product_id),
    constraint chk_no_self_reference check (parent_product_id <> component_product_id)
);
create index idx_component_parent on product_component (parent_product_id);
create index idx_component_child  on product_component (component_product_id);

-- Resolved component consumption, frozen at sale time (mirrors the sale_item
-- historical-snapshot pattern: name frozen, product_id nullable so deleting a
-- product later never breaks past receipts/inventory math).
create table sale_item_component (
    id            uuid primary key,
    sale_item_id  uuid not null references sale_item (id) on delete cascade,
    product_id    uuid references product (id) on delete set null,
    name          varchar(120) not null,
    qty           int not null check (qty > 0)
);
create index idx_sale_item_component_item on sale_item_component (sale_item_id);
