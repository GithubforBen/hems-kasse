package de.hems.kasse.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * One product's line within an Inventur. {@code expectedQty}/{@code diffQty} are computed and
 * frozen at write time (mirrors {@link de.hems.kasse.shift.Shift}'s expectedCashCents/diffCents),
 * so historical Fehlbestand reports stay stable even if later sales/intakes would change a live
 * recalculation.
 */
@Entity
@Table(name = "inventory_count_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryCountLine {
    @Id
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(name = "counted_qty", nullable = false)
    private int countedQty;

    @Column(name = "expected_qty", nullable = false)
    private int expectedQty;

    @Column(name = "diff_qty", nullable = false)
    private int diffQty;
}
