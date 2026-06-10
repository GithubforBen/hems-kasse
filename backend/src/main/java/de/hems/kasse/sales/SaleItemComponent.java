package de.hems.kasse.sales;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Resolved component consumption, frozen at sale time (mirrors {@link SaleItem}'s
 * historical-snapshot fields: {@code name} is captured once and never changes
 * even if the underlying product/recipe is edited or deleted later).
 */
@Entity
@Table(name = "sale_item_component")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItemComponent {
    @Id
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int qty;
}
