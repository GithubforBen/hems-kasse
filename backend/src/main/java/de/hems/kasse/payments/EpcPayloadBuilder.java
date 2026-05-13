package de.hems.kasse.payments;

import de.hems.kasse.config.KasseProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Builds an EPC069-12 v002 (Girocode) plain-text payload for a SEPA credit transfer.
 * <pre>
 *   BCD\n
 *   002\n
 *   1\n                              (character set: 1 = UTF-8)
 *   SCT\n
 *   &lt;BIC or empty&gt;\n
 *   &lt;Beneficiary name&gt;\n
 *   &lt;IBAN&gt;\n
 *   EUR&lt;amount&gt;\n
 *   &lt;Purpose 4-char or empty&gt;\n
 *   &lt;Structured ref or empty&gt;\n
 *   &lt;Unstructured remittance&gt;
 * </pre>
 */
@Component
public class EpcPayloadBuilder {

    private static final int NAME_MAX = 70;
    private static final int REMITTANCE_MAX = 140;
    private static final BigDecimal AMOUNT_MIN = new BigDecimal("0.01");
    private static final BigDecimal AMOUNT_MAX = new BigDecimal("999999.99");

    private final KasseProperties.Epc cfg;

    public EpcPayloadBuilder(KasseProperties props) {
        this.cfg = props.getEpc();
    }

    @PostConstruct
    void validateAtStartup() {
        if (cfg.getIban() == null || cfg.getIban().isBlank()) {
            throw new IllegalStateException(
                    "KASSE_EPC_IBAN is not set — required for Karte / EPC-QR payments");
        }
        if (cfg.getName() == null || cfg.getName().isBlank()) {
            throw new IllegalStateException("KASSE_EPC_NAME is not set");
        }
        if (!isValidIban(cfg.getIban())) {
            throw new IllegalStateException("KASSE_EPC_IBAN is not a valid IBAN: " + cfg.getIban());
        }
    }

    public String build(int amountCents, String extraRemittance) {
        BigDecimal amount = BigDecimal.valueOf(amountCents).movePointLeft(2);
        if (amount.compareTo(AMOUNT_MIN) < 0 || amount.compareTo(AMOUNT_MAX) > 0) {
            throw new IllegalArgumentException("amount out of range (0,01 € – 999.999,99 €)");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);

        String name = trimToLength(clean(cfg.getName()), NAME_MAX);
        String iban = stripIban(cfg.getIban());
        String bic = cfg.getBic() == null ? "" : clean(cfg.getBic()).replace(" ", "");

        String remittance = trimToLength(combinedRemittance(extraRemittance), REMITTANCE_MAX);
        String purpose = cfg.getPurpose() == null ? "" : clean(cfg.getPurpose());
        if (!purpose.isEmpty() && purpose.length() > 4) {
            purpose = purpose.substring(0, 4);
        }

        String body = "BCD\n"
                + "002\n"
                + "1\n"
                + "SCT\n"
                + bic + "\n"
                + name + "\n"
                + iban + "\n"
                + "EUR" + amount.toPlainString() + "\n"
                + purpose + "\n"
                + "\n"  // structured reference (RfU here)
                + remittance;
        // Spec cap: 331 bytes UTF-8
        if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 331) {
            throw new IllegalArgumentException("EPC payload exceeds 331 bytes");
        }
        return body;
    }

    private String combinedRemittance(String extra) {
        String base = cfg.getRemittance() == null ? "" : clean(cfg.getRemittance());
        if (extra == null || extra.isBlank()) return base;
        String s = clean(extra);
        return base.isEmpty() ? s : base + " · " + s;
    }

    /** NFC + drop control chars; keeps German umlauts (the EPC v002 UTF-8 set allows them). */
    static String clean(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') { out.append(' '); continue; }
            if (Character.isISOControl(c)) continue;
            out.append(c);
        }
        return out.toString();
    }

    static String trimToLength(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    static String stripIban(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /** ISO 7064 mod-97 IBAN validation (length 15–34, mod-97 == 1). */
    public static boolean isValidIban(String raw) {
        if (raw == null) return false;
        String s = stripIban(raw);
        if (s.length() < 15 || s.length() > 34) return false;
        String rearranged = s.substring(4) + s.substring(0, 4);
        StringBuilder digits = new StringBuilder(rearranged.length() * 2);
        for (int i = 0; i < rearranged.length(); i++) {
            char c = rearranged.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
            else if (c >= 'A' && c <= 'Z') digits.append(c - 'A' + 10);
            else return false;
        }
        // Mod-97 on a potentially long number — fold piecewise.
        int rem = 0;
        for (int i = 0; i < digits.length(); i++) {
            rem = (rem * 10 + (digits.charAt(i) - '0')) % 97;
        }
        return rem == 1;
    }
}
