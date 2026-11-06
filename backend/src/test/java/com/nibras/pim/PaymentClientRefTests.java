package com.nibras.pim;

import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A payment sent again by the phone's offline outbox (same clientRef) is recorded once. */
class PaymentClientRefTests extends AuthTestSupport {

    private String body(String ref) {
        return """
                {"amount": 12.5, "summary": "outbox test", "notes": "from the phone",
                 "date": "2026-11-05T09:30:00", "paid": true, "clientRef": "%s"}""".formatted(ref);
    }

    private long create(String json) throws Exception {
        String out = mvc.perform(post("/api/expenses").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(12.5))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(out).get("id").asLong();
    }

    @Test
    void theSameClientRefTwiceMakesOnePayment() throws Exception {
        String ref = "test-" + UUID.randomUUID();
        long first = create(body(ref));
        long again = create(body(ref));
        assertThat(again).isEqualTo(first);
    }

    @Test
    void withoutAClientRefEachCreateIsANewPayment() throws Exception {
        String json = """
                {"amount": 12.5, "summary": "no ref", "date": "2026-11-05T09:30:00"}""";
        assertThat(create(json)).isNotEqualTo(create(json));
    }
}
