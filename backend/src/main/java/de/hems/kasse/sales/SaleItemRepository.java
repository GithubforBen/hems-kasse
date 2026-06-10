package de.hems.kasse.sales;

import de.hems.kasse.inventory.ProductQty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    /**
     * Direct consumption: simple-product sale lines (no resolved components — i.e. the button
     * itself was not a Verkaufstaste) consume exactly {@code qty}× of their own product.
     */
    @Query(value = """
        select si.product_id as productId, sum(si.qty) as qty
        from sale_item si
        join sale s on s.id = si.sale_id
        where s.ts >= :from and s.ts < :to
          and si.product_id is not null
          and not exists (select 1 from sale_item_component sic where sic.sale_item_id = si.id)
        group by si.product_id
        """, nativeQuery = true)
    List<ProductQty> directConsumption(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Resolved-component consumption: Verkaufstasten (composed sale buttons) consume their
     * underlying products, frozen at sale time as {@code sale_item_component} rows.
     */
    @Query(value = """
        select sic.product_id as productId, sum(sic.qty) as qty
        from sale_item_component sic
        join sale_item si on si.id = sic.sale_item_id
        join sale s on s.id = si.sale_id
        where s.ts >= :from and s.ts < :to
          and sic.product_id is not null
        group by sic.product_id
        """, nativeQuery = true)
    List<ProductQty> componentConsumption(@Param("from") Instant from, @Param("to") Instant to);
}
