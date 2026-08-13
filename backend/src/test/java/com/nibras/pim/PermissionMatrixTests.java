package com.nibras.pim;

import com.fasterxml.jackson.databind.JsonNode;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The role→capability matrix screen, over the real HTTP endpoints.
 *
 * <p>The case that matters here is <b>adding a capability to a role that already has one</b>.
 * Writing the matrix used to clear the role's rows and re-insert the whole set, which Hibernate
 * flushes INSERT-first: the row for a key the role was keeping collided with
 * {@code uk_role_permission} before the delete cleared it, and the request 500'd. Every "grant the
 * first capability" test passed, because with nothing to keep there is nothing to collide with —
 * so the tests to keep are the ones that keep something.
 */
class PermissionMatrixTests extends AuthTestSupport {

    private static final String DATA_ENTRY = "/api/permissions/DATA_ENTRY";
    private static final String READ_ONLY = "/api/permissions/READ_ONLY";

    /** Whatever a test did, put the two roles back to their seeded capabilities. */
    @AfterEach
    void restoreDefaults() throws Exception {
        set(DATA_ENTRY, "content.write");
        set(READ_ONLY);
    }

    private String set(String path, String... permissions) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("permissions", List.of(permissions)));
        return mvc.perform(put(path).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> held(String json, String role) throws Exception {
        JsonNode keys = objectMapper.readTree(json).path("roles").path(role);
        return objectMapper.convertValue(keys, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, String.class));
    }

    @Test
    void grantsAnExtraCapabilityToARoleThatAlreadyHasOne() throws Exception {
        // Exactly what the matrix screen sends when the Audit box is ticked on the Data entry row:
        // the keys it already holds, plus the new one.
        String after = set(DATA_ENTRY, "content.write", "tools.audit");

        assertThat(held(after, "DATA_ENTRY"))
                .containsExactlyInAnyOrder("content.write", "tools.audit");
    }

    @Test
    void theGrantSurvivesTheRequestThatMadeIt() throws Exception {
        set(DATA_ENTRY, "content.write", "tools.audit");

        // Re-read rather than trusting the PUT's own response: the cache is rebuilt from a query,
        // so this is what proves the rows actually reached the database.
        String matrix = mvc.perform(get("/api/permissions").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(held(matrix, "DATA_ENTRY")).contains("tools.audit", "content.write");
    }

    @Test
    void revokesWithoutDisturbingTheOthers() throws Exception {
        set(DATA_ENTRY, "content.write", "tools.audit", "tools.settings");

        String after = set(DATA_ENTRY, "content.write", "tools.settings");

        assertThat(held(after, "DATA_ENTRY"))
                .containsExactlyInAnyOrder("content.write", "tools.settings");
    }

    @Test
    void writingTheSameSetTwiceIsHarmless() throws Exception {
        set(DATA_ENTRY, "content.write", "tools.audit");
        String again = set(DATA_ENTRY, "content.write", "tools.audit");

        assertThat(held(again, "DATA_ENTRY"))
                .containsExactlyInAnyOrder("content.write", "tools.audit");
    }

    @Test
    void grantingTheFirstCapabilityStillWorks() throws Exception {
        // The case that always passed — kept so a fix aimed at the other one can't break it.
        String after = set(READ_ONLY, "tools.audit");

        assertThat(held(after, "READ_ONLY")).containsExactly("tools.audit");
    }

    @Test
    void clearingEveryCapabilityLeavesTheRoleEmpty() throws Exception {
        set(DATA_ENTRY, "content.write", "tools.audit");

        String after = set(DATA_ENTRY);

        assertThat(held(after, "DATA_ENTRY")).isEmpty();
    }

    @Test
    void adminCannotBeEdited() throws Exception {
        mvc.perform(put("/api/permissions/ADMIN").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
