package com.nibras.pim.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nibras.pim.domain.AppUser;
import com.nibras.pim.repo.AppUserRepository;
import com.nibras.pim.security.Role;
import com.nibras.pim.web.dto.LoginRequest;
import com.nibras.pim.web.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared base for the 3-gate suite: upserts a known ADMIN test user and logs in through the real
 * {@code POST /api/auth/login} endpoint (not a mocked security context), so every test exercises
 * the actual auth path.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}: with {@code open-in-view: false}, each real
 * HTTP request gets its own independently-flushing persistence context — a test-managed
 * transaction wrapping every MockMvc call in one long-lived transaction instead breaks that (a
 * soft-delete's flush becomes invisible to a "later" raw-JDBC read, a fresh {@code load()} can
 * return a stale first-level-cache hit, etc.) in ways that don't happen in production. Each test
 * commits for real against the disposable {@code db_nibras2_test} clone; test data uses
 * recognizable prefixes ({@code gate2-}, {@code gate3-}) and count assertions tolerate
 * accumulation across repeated runs, so no cleanup step is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AuthTestSupport {

    protected static final String TEST_USERNAME = "nbr-test-admin";
    protected static final String TEST_PASSWORD = "test-password-Aa1!";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected AppUserRepository users;
    @Autowired
    protected PasswordEncoder encoder;
    @Autowired
    protected ObjectMapper objectMapper;

    protected String jwt;

    @BeforeEach
    void ensureTestUserAndLogIn() throws Exception {
        AppUser u = users.findByUsername(TEST_USERNAME).orElseGet(AppUser::new);
        u.setUsername(TEST_USERNAME);
        u.setPasswordHash(encoder.encode(TEST_PASSWORD));
        u.setDisplayName("Test Admin");
        u.setRole(Role.ADMIN);
        u.setEnabled(true);
        u.setMustChangePassword(false);
        users.save(u);

        String body = objectMapper.writeValueAsString(new LoginRequest(TEST_USERNAME, TEST_PASSWORD));
        String resp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        jwt = objectMapper.readValue(resp, LoginResponse.class).token();
    }

    protected String bearer() {
        return "Bearer " + jwt;
    }
}
