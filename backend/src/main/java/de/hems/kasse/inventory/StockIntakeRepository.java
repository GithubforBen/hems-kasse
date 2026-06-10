package de.hems.kasse.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StockIntakeRepository extends JpaRepository<StockIntake, UUID> {
    List<StockIntake> findAllByOrderByTsDesc();

    @Query(value = """
        select sil.product_id as productId, sum(sil.qty) as qty
        from stock_intake_line sil
        join stock_intake si on si.id = sil.stock_intake_id
        where si.ts >= :from and si.ts < :to
          and sil.product_id is not null
        group by sil.product_id
        """, nativeQuery = true)
    List<ProductQty> intakeQuantities(@Param("from") Instant from, @Param("to") Instant to);
}
