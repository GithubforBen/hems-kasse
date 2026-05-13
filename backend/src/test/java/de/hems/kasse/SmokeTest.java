package de.hems.kasse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmokeTest {

    @Autowired
    MockMvc mvc;

    @Test
    void contextStartsAndCategoriesAreSeeded_evenIfAuthRequired() throws Exception {
        // /api/categories is authenticated → 401 without token. Just verifies context boot + filter chain.
        mvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verkaufLoginSucceedsAndCategoriesReturnSeededData() throws Exception {
        String body = """
                {"role":"VERKAUF","name":"Timo","klasse":"BG12e","password":"Passw0rd"}
                """;
        var login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("VERKAUF"))
                .andReturn();
        String token = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(login.getResponse().getContentAsString())
                .get("token").asText();

        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("Kuchen"));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        String body = """
                {"role":"VERKAUF","name":"X","klasse":"BG12e","password":"nope"}
                """;
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void epcQrEndpointReturnsPng() throws Exception {
        String body = """
                {"role":"ADMIN","name":"alice","password":"adminPW1"}
                """;
        var login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String token = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(login.getResponse().getContentAsString())
                .get("token").asText();

        mvc.perform(get("/api/payments/epc-qr.png?amountCents=350")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void minimalShiftEndToEnd_andCsvExport() throws Exception {
        // Verkauf logs in
        String loginBody = """
                {"role":"VERKAUF","name":"Timo","klasse":"BG12e","password":"Passw0rd"}
                """;
        var login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk()).andReturn();
        String token = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(login.getResponse().getContentAsString())
                .get("token").asText();

        // Find a real product id (Schokokuchen, 1,50 €)
        var cats = mvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        String productId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(cats.getResponse().getContentAsString())
                .get(0).get("products").get(0).get("id").asText();

        // Open the shift (lazy on first hit)
        mvc.perform(get("/api/shifts/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Record one BAR sale of 2× 1,50 € = 3,00 €, gave 5,00 €
        String saleBody = """
                {"method":"BAR","givenCents":500,"items":[{"productId":"%s","qty":2}]}
                """.formatted(productId);
        mvc.perform(post("/api/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody))
                .andExpect(status().isOk());

        // Close the shift — counted == expected (opening 50,00 + cash 3,00 = 53,00 € → 5300 cents)
        String closeBody = "{\"countedCashCents\":5300,\"notes\":\"\"}";
        var closed = mvc.perform(post("/api/shifts/current/close")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diffCents").value(0))
                .andExpect(jsonPath("$.totalSalesCents").value(300))
                .andReturn();
        String shiftId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(closed.getResponse().getContentAsString())
                .get("id").asText();

        // CSV: items report for the closed shift
        var itemsCsv = mvc.perform(get("/api/shifts/" + shiftId + "/export.csv?type=items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=utf-8"))
                .andReturn().getResponse().getContentAsString();
        // Header + one item row, German money
        assertTrue(itemsCsv.startsWith("﻿"), "BOM at start: " + itemsCsv);
        assertTrue(itemsCsv.contains("Produkt;Menge"), itemsCsv);
        assertTrue(itemsCsv.contains(";Schokokuchen;2;1,50;3,00"), itemsCsv);

        // CSV: products aggregation
        var prodCsv = mvc.perform(get("/api/shifts/" + shiftId + "/export.csv?type=products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(prodCsv.contains("Rang;Produkt;Menge"), prodCsv);
        assertTrue(prodCsv.contains("1;Schokokuchen;2;100,0;3,00;100,0"), prodCsv);

        // CSV: mine
        var mineCsv = mvc.perform(get("/api/shifts/mine/export.csv?type=shifts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(mineCsv.contains("Schicht-ID;Verkäufer:in;Klasse;Rolle"), mineCsv);
        assertTrue(mineCsv.contains(";Timo;BG12e;VERKAUF;"), mineCsv);

        // Admin "all" requires admin role — Verkauf gets 403.
        mvc.perform(get("/api/shifts/export.csv?type=shifts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private static boolean assertTrue(boolean cond, String msg) {
        org.junit.jupiter.api.Assertions.assertTrue(cond, msg);
        return cond;
    }
}
