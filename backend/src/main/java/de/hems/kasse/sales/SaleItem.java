package de.hems.kasse.sales;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sale_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItem {
    @Id
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false, length = 20)
    private String color;

    /** Read-only back-reference to the owning sale (Sale.items remains the cascading owner). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", insertable = false, updatable = false)
    private Sale sale;

    /** Resolved underlying-product consumption for composed products (Verkaufstasten); empty for simple products. */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "sale_item_id", nullable = false)
    @Builder.Default
    private List<SaleItemComponent> components = new ArrayList<>();
}
