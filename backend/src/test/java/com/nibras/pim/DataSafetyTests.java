package com.nibras.pim;

import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate 3 (Data safety) — schema-migration idempotency and the soft-delete contract, both
 * verified rather than trusted (this is the gate that most directly earns "trust in production,"
 * per the project's own testing methodology).
 */
class DataSafetyTests extends AuthTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void extraSchemaSqlIsIdempotent() throws Exception {
        File sqlFile = new File(System.getProperty("user.dir"), "../db/extra-schema.sql").getCanonicalFile();
        assertThat(sqlFile).as("db/extra-schema.sql should exist").exists();

        // Same command used for every manual verification of this file this project has ever had:
        // mysql db_nibras2_test < extra-schema.sql (credentials from ~/.my.cnf). Run twice — the file's own
        // docstring promises it's safe to re-run; this test verifies that promise, not just trusts it.
        runMysqlScript(sqlFile);
        runMysqlScript(sqlFile);
    }

    private void runMysqlScript(File sqlFile) throws Exception {
        Process p = new ProcessBuilder("mysql", "db_nibras2_test")
                .redirectInput(sqlFile)
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        assertThat(exit).as("mysql exit code (output: %s)", output).isZero();
    }

    @Test
    void softDeleteLeavesTheRowPhysicallyPresent() throws Exception {
        String createResp = mvc.perform(post("/api/tasks").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"summary":"gate3-soft-delete-task"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResp).get("id").asLong();

        mvc.perform(delete("/api/tasks/" + id).header("Authorization", bearer()))
                .andExpect(status().isNoContent());

        // Bypass Hibernate's @SQLRestriction with a raw query — the physical row must still exist,
        // with deleted_on stamped, not actually removed.
        List<Map<String, Object>> rows = jdbc.queryForList("select id, deleted_on from task where id = ?", id);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("deleted_on")).isNotNull();
    }
}
