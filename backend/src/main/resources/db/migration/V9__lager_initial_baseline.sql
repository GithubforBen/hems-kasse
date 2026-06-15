-- V7 (Lager) shipped without any starting baseline. InventoryService.currentExpectedStock()
-- and recordCount() both fall back to "no previous count" -> baseline 0, consumption summed
-- from Instant.EPOCH. For every product that already had sales history before Lager existed,
-- that means "Erwartet" shows a large negative number (0 minus all-time consumption) instead
-- of something usable.
--
-- Seed a zero-counted baseline Inventur "now" for every trackable product (mirrors a real
-- Inventur with countedQty = 0 as a placeholder). From this point on, expected-stock
-- calculations only look at intakes/consumption since this baseline, which is sane. The
-- admin can immediately run a real Inventur with the actual counted quantities to overwrite
-- these placeholders.
insert into inventory_count (id, ts, by_name, notes) values
    ('00000000-0000-0000-0000-000000000701', now(), 'System',
     'Anfangsbestand (automatisch erzeugt) – bitte mit einer echten Inventur überschreiben');

insert into inventory_count_line (id, inventory_count_id, product_id, product_name, counted_qty, expected_qty, diff_qty)
select gen_random_uuid(), '00000000-0000-0000-0000-000000000701', id, name, 0, 0, 0
from product
where not variable and not composed;
