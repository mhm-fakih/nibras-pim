package com.nibras.pim;

import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate 1 (Smoke) — "did it even start?" The context-load itself (implicit: if wiring is broken,
 * every test in this class fails to even run) is the most valuable single assertion here.
 */
class SmokeTests extends AuthTestSupport {

    @Test
    void modulesEndpointResponds() throws Exception {
        mvc.perform(get("/api/modules").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        // Confirms the security filter chain is actually wired, not just present in source.
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }
}
