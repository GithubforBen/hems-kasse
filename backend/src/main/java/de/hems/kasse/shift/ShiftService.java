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

    /** Highest envelope number we accept — keeps typos like a scanned barcode out of the books. */
    public static final int MAX_ABRECHNUNG_NR = 999_999;

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
     * Guards the envelope number a cashier typed at login. A number that already belongs to a
     * closed Abrechnung must never be reused — its envelope has been handed in and its figures
     * are final. Called both at login (fail fast, before a session exists) and again when a
     * shift is actually opened, because the Abrechnung may have been closed in between — for
     * instance by a colleague, or by this very device before the page was reloaded.
     *
     * @throws ResponseStatusException 400 if the number is missing or out of range,
     *                                 409 if it belongs to an already closed Abrechnung
     */
    public void assertUsable(Integer abrechnungNr) {
        if (abrechnungNr == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Abrechnungs-Nr. fehlt");
        }
        if (abrechnungNr < 1 || abrechnungNr > MAX_ABRECHNUNG_NR) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Abrechnungs-Nr. muss zwischen 1 und " + MAX_ABRECHNUNG_NR + " liegen");
        }
        shifts.findByAbrechnungNr(abrechnungNr)
                .filter(Shift::isClosed)
                .ifPresent(closed -> {
                    throw new ResponseStatusException(CONFLICT,
                            "Abrechnung #" + abrechnungNr + " wurde bereits abgeschlossen. "
                                    + "Bitte die Nummer des nächsten Umschlags verwenden.");
                });
    }

    /**
     * Returns the caller's open shift for the given Kassette, opening a new one if none is
     * active. VERKAUF callers must supply a registerId (each register runs its own independent
     * shift); ADMIN callers never select a register and keep the legacy single-shift behaviour.
     *
     * <p>Several cashiers of the same group share one shift per register: the first to arrive
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
        var running = shifts.findFirstBySubjectKeyAndRegisterIdAndClosedAtIsNull(p.subjectKey(), registerId);
        if (running.isPresent()) {
            return joinRunning(running.get(), p);
        }
        assertUsable(p.abrechnungNr());
        return openOrJoin(p, register);
    }

    /**
     * The caller's group already has this Kassette open. Joining is only correct when both are
     * working out of the same envelope; otherwise one of the two would book sales into an
     * Abrechnung that will be handed in under a different number.
     */
    private Shift joinRunning(Shift running, KassePrincipal p) {
        Integer open = running.getAbrechnungNr();
        if (open != null && !open.equals(p.abrechnungNr())) {
            throw new ResponseStatusException(CONFLICT,
                    "An " + running.getRegisterName() + " läuft bereits Abrechnung #" + open
                            + ". Bitte mit dieser Nummer anmelden oder die Abrechnung zuerst abschließen.");
        }
        return running;
    }

    private Shift openOrJoin(KassePrincipal p, Register register) {
        try {
            return self.openShift(p, register);
        } catch (DataIntegrityViolationException race) {
            // Two indexes can reject this INSERT. Either a cashier of the same group opened this
            // register's shift concurrently — then join the one that won — or the envelope number
            // is in use elsewhere, which is a mistake the cashier has to resolve at the counter.
            var winner = shifts.findFirstBySubjectKeyAndRegisterIdAndClosedAtIsNull(
                    p.subjectKey(), register.getId());
            if (winner.isPresent()) {
                return joinRunning(winner.get(), p);
            }
            throw abrechnungInUse(p.abrechnungNr(), race);
        }
    }

    /**
     * Turns a unique-index violation on the envelope number into a message that names where the
     * envelope is being used, so nobody has to guess why the login was refused.
     */
    private ResponseStatusException abrechnungInUse(Integer abrechnungNr, DataIntegrityViolationException race) {
        Shift other = shifts.findByAbrechnungNr(abrechnungNr).orElse(null);
        if (other == null) throw race;
        String where = other.isClosed()
                ? "wurde bereits abgeschlossen"
                : "läuft bereits für Gruppe " + other.getGruppe() + " an " + other.getRegisterName();
        return new ResponseStatusException(CONFLICT,
                "Abrechnung #" + abrechnungNr + " " + where
                        + ". Bitte die Nummer des nächsten Umschlags verwenden.");
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
                .gruppe(p.gruppe())
                .abrechnungNr(p.abrechnungNr())
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
