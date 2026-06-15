-- Move all historically-sold products into a dedicated "alt" category.
-- Products that appear in sale_item.product_id were sold in past events and are
-- considered legacy/archive — they should not clutter the active product catalog
-- but must remain in the DB for stats, CSV exports, and inventory history.

insert into category (id, name, color, sort_order)
values ('00000000-0000-0000-0000-000000000a01', 'alt', 'lavender', 99);

update product
set category_id = '00000000-0000-0000-0000-000000000a01'
where id in (
    select distinct product_id
    from sale_item
    where product_id is not null
);
