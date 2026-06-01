package de.hems.kasse.shift;

import de.hems.kasse.auth.KassePrincipal;
import de.hems.kasse.sales.PaymentMethod;
import de.hems.kasse.sales.Sale;
import de.hems.kasse.sales.SaleRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
public class ShiftService {

    private final ShiftRepository shifts;
    private final SaleRepository sales;

    public ShiftService(ShiftRepository shifts, SaleRepository sales) {
        this.shifts = shifts;
        this.sales = sales;
    }

    /** Returns the caller's open shift, opening a new one if none is active. */
    @Transactional
    public Shift currentOrOpen(KassePrincipal p) {
        return shifts.findFirstBySubjectKeyAndClosedAtIsNull(p.subjectKey())
                .orElseGet(() -> shifts.save(Shift.builder()
                        .id(UUID.randomUUID())
                        .subjectKey(p.subjectKey())
                        .userName(p.name())
                        .role(p.role().name())
                        .klasse(p.klasse())
                        .openingCashCents(5000) // matches prototype default 50,00 €
                        .startedAt(Instant.now())
                        .build()));
    }

    @Transactional
    public Shift setOpeningCash(KassePrincipal p, int openingCashCents, String notes) {
        Shift s = currentOrOpen(p);
        if (openingCashCents >= 0) s.setOpeningCashCents(openingCashCents);
        if (notes != null) s.setNotes(notes);
        return shifts.save(s);
    }

    @Transactional
    public Shift close(KassePrincipal p, int countedCashCents, String notes) {
        Shift s = shifts.findFirstBySubjectKeyAndClosedAtIsNull(p.subjectKey())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Keine offene Schicht"));
        List<Sale> shiftSales = sales.findAllByShiftIdOrderByTsDesc(s.getId());

        int cash = shiftSales.stream().filter(x -> x.getMethod() == PaymentMethod.BAR)
                .mapToInt(Sale::getTotalCents).sum();
        int card = shiftSales.stream().filter(x -> x.getMethod() == PaymentMethod.KARTE)
                .mapToInt(Sale::getTotalCents).sum();
        int paypal = shiftSales.stream().filter(x -> x.getMethod() == PaymentMethod.PAYPAL)
                .mapToInt(Sale::getTotalCents).sum();
        int total = cash + card + paypal;
        int expected = s.getOpeningCashCents() + cash;
        int diff = countedCashCents - expected;
        int itemsSold = shiftSales.stream()
                .flatMap(x -> x.getItems().stream())
                .mapToInt(it -> it.getQty()).sum();

        s.setClosedAt(Instant.now());
        s.setCountedCashCents(countedCashCents);
        s.setExpectedCashCents(expected);
        s.setDiffCents(diff);
        s.setCashSalesCents(cash);
        s.setCardSalesCents(card);
        s.setPaypalSalesCents(paypal);
        s.setTotalSalesCents(total);
        s.setSalesCount(shiftSales.size());
        s.setItemsSold(itemsSold);
        if (notes != null) s.setNotes(notes);
        return shifts.save(s);
    }

    public Shift forCaller(KassePrincipal p, UUID id) {
        Shift s = shifts.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        boolean isOwner = s.getSubjectKey().equals(p.subjectKey());
        boolean isAdmin = p.role() == de.hems.kasse.auth.Role.ADMIN;
        if (!isOwner && !isAdmin) throw new ResponseStatusException(FORBIDDEN);
        return s;
    }
}
