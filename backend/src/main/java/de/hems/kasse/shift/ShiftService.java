package de.hems.kasse.shift;

import de.hems.kasse.auth.KassePrincipal;
import de.hems.kasse.auth.Role;
import de.hems.kasse.register.Register;
import de.hems.kasse.register.RegisterRepository;
import de.hems.kasse.sales.PaymentMethod;
import de.hems.kasse.sales.Sale;
import de.hems.kasse.sales.SaleRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final RegisterRepository registers;
    /** Self-reference so the REQUIRES_NEW transaction on {@link #openShift} is actually applied. */
    private final ShiftService self;

    public ShiftService(ShiftRepository shifts, SaleRepository sales, RegisterRepository registers,
                        @Lazy ShiftService self) {
        this.shifts = shifts;
        this.sales = sales;
        this.registers = registers;
        this.self = self;
    }

    /**
     * Returns the caller's open shift for the given Kassette, opening a new one if none is
     * active. VERKAUF callers must supply a registerId (each register runs its own independent
     * shift); ADMIN callers never select a register and keep the legacy single-shift behaviour.
     *
     * <p>Several cashiers of the same class share one shift per register: the first to arrive
     * opens it, everyone after joins the running shift. If two of them open the very first
     * shift of a register at the same instant, the partial unique index lets exactly one INSERT
     * win; the loser catches the conflict and joins the winner's shift instead of erroring.
     */
    public Shift currentOrOpen(KassePrincipal p, UUID registerId) {
        if (p.role() == Role.VERKAUF && registerId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Kassette fehlt");
        }
        if (registerId == null) {
            return shifts.findFirstBySubjectKeyAndClosedAtIsNull(p.subjectKey())
                    .orElseGet(() -> self.openShift(p, null));
        }
        Register register = registers.findById(registerId)
                .filter(Register::isActive)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unbekannte Kassette"));
        return shifts.findFirstBySubjectKeyAndRegisterIdAndClosedAtIsNull(p.subjectKey(), registerId)
                .orElseGet(() -> openOrJoin(p, register));
    }

    private Shift openOrJoin(KassePrincipal p, Register register) {
        try {
            return self.openShift(p, register);
        } catch (DataIntegrityViolationException race) {
            // A cashier of the same class opened this register's first shift concurrently —
            // join the one that won the INSERT instead of surfacing a 500.
            return shifts.findFirstBySubjectKeyAndRegisterIdAndClosedAtIsNull(p.subjectKey(), register.getId())
                    .orElseThrow(() -> race);
        }
    }

    /**
     * Creates and commits a new shift in its own transaction so a unique-index conflict here
     * rolls back only this insert, leaving the caller's transaction intact to re-read the winner.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Shift openShift(KassePrincipal p, Register register) {
        return shifts.save(newShift(p, register));
    }

    private Shift newShift(KassePrincipal p, Register register) {
        return Shift.builder()
                .id(UUID.randomUUID())
                .subjectKey(p.subjectKey())
                .userName(p.name())
                .role(p.role().name())
                .klasse(p.klasse())
                .registerId(register != null ? register.getId() : null)
                .registerName(register != null ? register.getName() : null)
                .openingCashCents(5000) // matches prototype default 50,00 €
                .startedAt(Instant.now())
                .build();
    }

    @Transactional
    public Shift setOpeningCash(KassePrincipal p, UUID registerId, int openingCashCents, String notes) {
        Shift s = currentOrOpen(p, registerId);
        if (openingCashCents >= 0) s.setOpeningCashCents(openingCashCents);
        if (notes != null) s.setNotes(notes);
        return shifts.save(s);
    }

    @Transactional
    public Shift close(KassePrincipal p, UUID registerId, int countedCashCents, String notes) {
        Shift s = (registerId == null
                ? shifts.findFirstBySubjectKeyAndClosedAtIsNull(p.subjectKey())
                : shifts.findFirstBySubjectKeyAndRegisterIdAndClosedAtIsNull(p.subjectKey(), registerId))
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
