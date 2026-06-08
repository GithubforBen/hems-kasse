package de.hems.kasse.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A "Wareneingang": a recorded delivery that adds to stock between Inventuren. */
@Entity
@Table(name = "stock_intake")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockIntake {
    @Id
    private UUID id;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "by_name", nullable = false, length = 120)
    private String byName;

    @Column(length = 2000)
    private String notes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_intake_id", nullable = false)
    @Builder.Default
    private List<StockIntakeLine> lines = new ArrayList<>();
}
