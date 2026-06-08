package de.hems.kasse.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** One delivered product within a Wareneingang. */
@Entity
@Table(name = "stock_intake_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockIntakeLine {
    @Id
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(nullable = false)
    private int qty;
}
