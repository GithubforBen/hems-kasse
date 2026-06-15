-- The V6 migration added the (optional, unique) plu column and the composed flag
-- (defaulting existing rows to false, i.e. Einzelprodukt), but left plu = null for
-- every product that existed before that migration. Backfill those with a random,
-- unique 4-digit PLU so they show up correctly in catalog/statistics exports.
do $$
declare
    r record;
    candidate text;
begin
    for r in select id from product where plu is null loop
        loop
            candidate := lpad((trunc(random() * 9000) + 1000)::int::text, 4, '0');
            if not exists (select 1 from product where plu = candidate) then
                exit;
            end if;
        end loop;
        update product set plu = candidate where id = r.id;
    end loop;
end $$;
