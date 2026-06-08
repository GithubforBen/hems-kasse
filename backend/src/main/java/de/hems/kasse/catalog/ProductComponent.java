package de.hems.kasse.catalog;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** One "ingredient" of a composed product (Verkaufstaste): consume {@code qty}× of {@code componentProduct}. */
@Entity
@Table(name = "product_component")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductComponent {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_product_id", nullable = false)
    private Product parentProduct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_product_id", nullable = false)
    private Product componentProduct;

    @Column(nullable = false)
    private int qty;
}
