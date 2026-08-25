package de.hems.kasse.shift;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shift")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift {
    @Id
    private UUID id;

    @Column(name = "subject_key", nullable = false, length = 200)
    private String subjectKey;

    @Column(name = "user_name", nullable = false, length = 120)
    private String userName;

    @Column(nullable = false, length = 20)
    private String role; // VERKAUF | ADMIN

    @Column(length = 40)
    private String gruppe;

    /** Nummer des Geldumschlags, mit dem diese Abrechnung abgegeben wird. */
    @Column(name = "abrechnung_nr")
    private Integer abrechnungNr;

    @Column(name = "register_id")
    private UUID registerId;

    @Column(name = "register_name", length = 80)
    private String registerName;

    @Column(name = "opening_cash_cents", nullable = false)
    private int openingCashCents;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "counted_cash_cents")
    private Integer countedCashCents;

    @Column(name = "expected_cash_cents")
    private Integer expectedCashCents;

    @Column(name = "diff_cents")
    private Integer diffCents;

    @Column(name = "cash_sales_cents")
    private Integer cashSalesCents;

    @Column(name = "card_sales_cents")
    private Integer cardSalesCents;

    @Column(name = "paypal_sales_cents")
    private Integer paypalSalesCents;

    @Column(name = "total_sales_cents")
    private Integer totalSalesCents;

    @Column(name = "sales_count")
    private Integer salesCount;

    @Column(name = "items_sold")
    private Integer itemsSold;

    @Column(length = 2000)
    private String notes;

    public boolean isClosed() {
        return closedAt != null;
    }
}
