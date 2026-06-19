-- Per-article discount controls.
--   discountable    : may a cashier apply a percentage discount to this article at all?
--   min_price_cents : optional price floor — a discount can never push the unit price below
--                     it (e.g. Red Bull stays >= 1,10 €). NULL means no floor (100% reaches 0).
alter table product add column discountable boolean not null default true;
alter table product add column min_price_cents int;

-- Record what was actually charged vs. the list price on each sold line, plus the discount.
alter table sale_item add column list_price_cents int not null default 0;
alter table sale_item add column discount_percent int not null default 0;

-- Historical lines were sold at list price with no discount.
update sale_item set list_price_cents = price_cents;
