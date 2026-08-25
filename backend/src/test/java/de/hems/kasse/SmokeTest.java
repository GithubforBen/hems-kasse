package de.hems.kasse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
                {"role":"VERKAUF","name":"Timo","gruppe":"1","password":"Passw0rd"}
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
                // 4 seeded categories + the "alt" archive category added in V10.
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].name").value("Kuchen"));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        String body = """
                {"role":"VERKAUF","name":"X","gruppe":"1","password":"nope"}
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
                {"role":"VERKAUF","name":"Timo","gruppe":"1","password":"Passw0rd"}
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

        // VERKAUF runs on a Kassette (default register seeded in V5).
        String register = "00000000-0000-0000-0000-000000000501";

        // Open the shift (lazy on first hit)
        mvc.perform(get("/api/shifts/current")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Kasse-Register-Id", register))
                .andExpect(status().isOk());

        // Record one BAR sale of 2× 1,50 € = 3,00 €, gave 5,00 €
        String saleBody = """
                {"method":"BAR","givenCents":500,"items":[{"productId":"%s","qty":2}]}
                """.formatted(productId);
        mvc.perform(post("/api/sales")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Kasse-Register-Id", register)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody))
                .andExpect(status().isOk());

        // Close the shift — counted == expected (opening 50,00 + cash 3,00 = 53,00 € → 5300 cents)
        String closeBody = "{\"countedCashCents\":5300,\"notes\":\"\"}";
        var closed = mvc.perform(post("/api/shifts/current/close")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Kasse-Register-Id", register)
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
        assertTrue(mineCsv.contains("Schicht-ID;Verkäufer:in;Gruppe;Kassette;Rolle"), mineCsv);
        assertTrue(mineCsv.contains(";Timo;1;Kassette 1;VERKAUF;"), mineCsv);

        // Admin "all" requires admin role — Verkauf gets 403.
        mvc.perform(get("/api/shifts/export.csv?type=shifts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void discountFlowRespectsFloorAndNonDiscountable() throws Exception {
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        String register = "00000000-0000-0000-0000-000000000501";

        // Admin configures the first Kuchen: list 1,50 €, price floor 1,30 €, discountable.
        String adminTok = token(login("ADMIN", "alice", null, "adminPW1"));
        var cats = om.readTree(mvc.perform(get("/api/categories").header("Authorization", "Bearer " + adminTok))
                .andReturn().getResponse().getContentAsString());
        var first = cats.get(0).get("products").get(0);
        String productId = first.get("id").asText();
        int list = first.get("priceCents").asInt();
        mvc.perform(patch("/api/products/" + productId).header("Authorization", "Bearer " + adminTok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minPriceCents\":130,\"discountable\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minPriceCents").value(130))
                .andExpect(jsonPath("$.discountable").value(true));

        // A second product made non-discountable.
        String lockedId = cats.get(0).get("products").get(1).get("id").asText();
        mvc.perform(patch("/api/products/" + lockedId).header("Authorization", "Bearer " + adminTok)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discountable\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountable").value(false));

        String tok = token(login("VERKAUF", "Timo", "1", "Passw0rd"));

        // 50 % off 1,50 € would be 0,75 € but the 1,30 € floor caps it.
        mvc.perform(post("/api/sales").header("Authorization", "Bearer " + tok)
                        .header("X-Kasse-Register-Id", register)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"BAR\",\"givenCents\":1000,\"items\":[{\"productId\":\"%s\",\"qty\":1,\"discountPercent\":50}]}".formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCents").value(130))
                .andExpect(jsonPath("$.items[0].priceCents").value(130))
                .andExpect(jsonPath("$.items[0].listPriceCents").value(list))
                .andExpect(jsonPath("$.items[0].discountPercent").value(50));

        // Even 100 % cannot go below the floor.
        mvc.perform(post("/api/sales").header("Authorization", "Bearer " + tok)
                        .header("X-Kasse-Register-Id", register)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"BAR\",\"givenCents\":1000,\"items\":[{\"productId\":\"%s\",\"qty\":1,\"discountPercent\":100}]}".formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCents").value(130));

        // Discounting a non-discountable product is rejected.
        mvc.perform(post("/api/sales").header("Authorization", "Bearer " + tok)
                        .header("X-Kasse-Register-Id", register)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"BAR\",\"givenCents\":1000,\"items\":[{\"productId\":\"%s\",\"qty\":1,\"discountPercent\":10}]}".formatted(lockedId)))
                .andExpect(status().isBadRequest());
    }

    private String login(String role, String name, String gruppe, String password) throws Exception {
        String body = (gruppe == null)
                ? "{\"role\":\"%s\",\"name\":\"%s\",\"password\":\"%s\"}".formatted(role, name, password)
                : "{\"role\":\"%s\",\"name\":\"%s\",\"gruppe\":\"%s\",\"password\":\"%s\"}".formatted(role, name, gruppe, password);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String token(String loginJson) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(loginJson).get("token").asText();
    }

    private static boolean assertTrue(boolean cond, String msg) {
        org.junit.jupiter.api.Assertions.assertTrue(cond, msg);
        return cond;
    }
}
