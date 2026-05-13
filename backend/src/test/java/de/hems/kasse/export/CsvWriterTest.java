package de.hems.kasse.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CsvWriterTest {

    @Test
    void writesBomAndSemicolonSeparatedRowsWithCrlf() {
        String out = new CsvWriter()
                .row("Name", "Preis (€)", "Notiz")
                .row("Kuchen", CsvWriter.euro(150), "frisch")
                .toCsv();

        // UTF-8 BOM, then header, then row.
        assertTrue(out.startsWith("﻿"), "Should begin with BOM");
        assertTrue(out.contains("Name;Preis (€);Notiz\r\n"));
        assertTrue(out.contains("Kuchen;1,50;frisch\r\n"));
    }

    @Test
    void escapesQuotesSeparatorsAndNewlines() {
        String out = new CsvWriter()
                .row("a;b", "say \"hi\"", "line\nbreak", "plain")
                .toCsv();
        assertTrue(out.contains("\"a;b\";\"say \"\"hi\"\"\";\"line\nbreak\";plain"));
    }

    @Test
    void euroAndSignedEuroFormatGerman() {
        assertEquals("12,50", CsvWriter.euro(1250));
        assertEquals("0,00", CsvWriter.euro(0));
        assertEquals("", CsvWriter.euro(null));

        assertEquals("+3,00", CsvWriter.signedEuro(300));
        assertEquals("-1,25", CsvWriter.signedEuro(-125));
        assertEquals("0,00", CsvWriter.signedEuro(0));
    }
}
