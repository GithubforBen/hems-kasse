package de.hems.kasse.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** An "Inventur": admin counts physical stock of one or more products at a point in time. */
@Entity
@Table(name = "inventory_count")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryCount {
    @Id
    private UUID id;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "by_name", nullable = false, length = 120)
    private String byName;

    @Column(length = 2000)
    private String notes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "inventory_count_id", nullable = false)
    @Builder.Default
    private List<InventoryCountLine> lines = new ArrayList<>();
}
