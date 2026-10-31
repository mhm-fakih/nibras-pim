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
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired
    private JdbcTemplate jdbc;

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

    /**
     * Task statuses (by the codes the tests ask for) and a journal type. Lookups are reference
     * data the app never creates, so the tests can't rely on the clone still having them: on
     * 2026-10-29 db_nibras2_test was rebuilt with empty lookup tables and twelve tests failed on
     * "no status done" / an empty journal_type.
     */
    @BeforeEach
    void ensureLookups() {
        String[][] statuses = {
                {"not-s", "Needs Action"}, {"inp", "In progress"}, {"done", "Completed"},
                {"def", "Deferred"}, {"dismissed", "Cancelled"}, {"froz", "Frozen"}};
        for (String[] s : statuses) {
            jdbc.update("""
                    INSERT INTO work_status (version, code, name, date_created, last_updated)
                    SELECT 0, ?, ?, NOW(), NOW() FROM DUAL
                    WHERE NOT EXISTS (SELECT 1 FROM work_status WHERE code = ? AND deleted_on IS NULL)""",
                    s[0], s[1], s[0]);
        }
        jdbc.update("""
                INSERT INTO journal_type (version, code, name, date_created, last_updated)
                SELECT 0, 'note', 'Note', NOW(), NOW() FROM DUAL
                WHERE NOT EXISTS (SELECT 1 FROM journal_type WHERE deleted_on IS NULL)""");
    }

    protected String bearer() {
        return "Bearer " + jwt;
    }
}
