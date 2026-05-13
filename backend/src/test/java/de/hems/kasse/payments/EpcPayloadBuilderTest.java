package de.hems.kasse.payments;

import de.hems.kasse.config.KasseProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EpcPayloadBuilderTest {

    private EpcPayloadBuilder builderWith(String iban, String name, String bic, String remittance) {
        KasseProperties p = new KasseProperties();
        p.getEpc().setIban(iban);
        p.getEpc().setName(name);
        p.getEpc().setBic(bic);
        p.getEpc().setRemittance(remittance);
        EpcPayloadBuilder b = new EpcPayloadBuilder(p);
        b.validateAtStartup();
        return b;
    }

    @Test
    void buildsExpectedLayoutForGermanIban() {
        var b = builderWith("DE89 3704 0044 0532 0130 00", "Schulkasse Beispielschule", "", "Kuchenverkauf");
        String body = b.build(350, "Schicht 42");
        String[] lines = body.split("\n", -1);
        assertEquals("BCD", lines[0]);
        assertEquals("002", lines[1]);
        assertEquals("1", lines[2]);
        assertEquals("SCT", lines[3]);
        assertEquals("", lines[4]);                      // BIC empty
        assertEquals("Schulkasse Beispielschule", lines[5]);
        assertEquals("DE89370400440532013000", lines[6]);
        assertEquals("EUR3.50", lines[7]);
        assertEquals("", lines[8]);                      // purpose empty
        assertEquals("", lines[9]);                      // structured ref empty
        assertEquals("Kuchenverkauf · Schicht 42", lines[10]);
    }

    @Test
    void rejectsInvalidIbanAtStartup() {
        KasseProperties p = new KasseProperties();
        p.getEpc().setIban("DE00 BOGUS");
        p.getEpc().setName("X");
        assertThrows(IllegalStateException.class,
                () -> new EpcPayloadBuilder(p).validateAtStartup());
    }

    @Test
    void rejectsZeroAndTooLargeAmount() {
        var b = builderWith("DE89370400440532013000", "X", "", "ref");
        assertThrows(IllegalArgumentException.class, () -> b.build(0, null));
        assertThrows(IllegalArgumentException.class, () -> b.build(100_000_000, null));
    }
}
