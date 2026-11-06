package com.nibras.pim;

import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A browser on the Tailscale HTTPS address can sign in with no per-machine setting. */
class TailscaleOriginTests extends AuthTestSupport {

    private static final String WRONG = """
            {"username":"no-such-user-origin-test","password":"x"}""";

    @Test
    void anyTailscaleHttpsAddressIsAllowed() throws Exception {
        mvc.perform(post("/api/auth/login").header("Origin", "https://t470s.tail98f579.ts.net")
                        .contentType(MediaType.APPLICATION_JSON).content(WRONG))
                .andExpect(status().isUnauthorized());   // reached the password check, not 403
    }

    @Test
    void anUnknownSiteIsStillRefused() throws Exception {
        mvc.perform(post("/api/auth/login").header("Origin", "https://evil.example.com")
                        .contentType(MediaType.APPLICATION_JSON).content(WRONG))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").header("Origin", "http://t470s.tail98f579.ts.net")
                        .contentType(MediaType.APPLICATION_JSON).content(WRONG))
                .andExpect(status().isForbidden());  // plain http is not the Tailscale HTTPS address
    }
}
