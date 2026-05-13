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
}
