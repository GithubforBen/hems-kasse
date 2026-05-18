package de.hems.kasse.sales;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sale")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {
    @Id
    private UUID id;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(nullable = false)
    private Instant ts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentMethod method;

    @Column(name = "total_cents", nullable = false)
    private int totalCents;

    @Column(name = "given_cents", nullable = false)
    private int givenCents;

    @Column(name = "change_cents", nullable = false)
    private int changeCents;

    @Column(name = "by_name", nullable = false, length = 120)
    private String byName;

    @Column(name = "transaction_ref", nullable = false, length = 12)
    private String transactionRef;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "sale_id", nullable = false)
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();
}
