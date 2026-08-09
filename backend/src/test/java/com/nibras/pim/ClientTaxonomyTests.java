package com.nibras.pim;

import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.repo.AppModuleRepository;
import com.nibras.pim.repo.DepartmentRepository;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.regex.Matcher;

/**
 * What the clients are told their vocabulary is: enabled modules, and departments of
 * priority >= 2.
 *
 * <p>The rule itself lives in {@link com.nibras.pim.service.ClientSyncViewService} and is shown on
 * the Admin page. These tests pin the SYNC route to the same service, because the phone reading a
 * different set from the one Admin claims it has is precisely the drift that page exists to expose.
 */
class ClientTaxonomyTests extends AuthTestSupport {

    /** Pulls the module sortOrders out of the response, in the order they appear. */
    private static final class Regex {
        static List<Integer> orders(String json) {
            Matcher m = java.util.regex.Pattern.compile("\"sortOrder\"\\s*:\\s*(\\d+)").matcher(json);
            var out = new java.util.ArrayList<Integer>();
            while (m.find()) out.add(Integer.parseInt(m.group(1)));
            return out;
        }
    }

    private final String tag = "ctax-" + System.nanoTime();

    @Autowired
    DepartmentRepository departments;

    @Autowired
    AppModuleRepository modules;

    private Department department(String name, Integer priority, Integer order) {
        Department d = new Department();
        d.setName(name);
        d.setPriority(priority);
        d.setOrderNumber(order);
        return departments.saveAndFlush(d);
    }

    private String taxonomy() throws Exception {
        return mvc.perform(get("/sync/clientTaxonomy").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void departmentsBelowPriorityTwoAreNotOfferedToClients() throws Exception {
        department(tag + "-high", 3, 1);
        department(tag + "-edge", 2, 2);     // the boundary is inclusive
        department(tag + "-low", 1, 3);
        department(tag + "-none", null, 4);  // never set — treated as not offered

        String body = taxonomy();

        assertThat(body).contains(tag + "-high").contains(tag + "-edge");
        // The long tail of low-priority departments is never offered on a phone keypad; sending it
        // would also make the Admin page's "what the clients see" list a fiction.
        assertThat(body).doesNotContain(tag + "-low").doesNotContain(tag + "-none");
    }

    @Test
    void modulesComeBackInTheSettingsPagesOrderColumn() throws Exception {
        String body = taxonomy();
        var orders = Regex.orders(body);

        // sortOrder, not a second sync-only order: one order the user maintains in one place.
        assertThat(orders).isSorted();
        assertThat(body).contains("\"sortOrder\"");
        assertThat(body).doesNotContain("\"syncOrder\"");
    }

    @Test
    void modulesAreOnlyTheEnabledOnes() throws Exception {
        String body = taxonomy();

        var enabled = modules.findAll().stream()
                .filter(m -> !Boolean.FALSE.equals(m.getEnabled()))
                .map(m -> m.getModuleKey()).toList();
        var disabled = modules.findAll().stream()
                .filter(m -> Boolean.FALSE.equals(m.getEnabled()))
                .map(m -> m.getModuleKey()).toList();

        assertThat(enabled).isNotEmpty();
        enabled.forEach(k -> assertThat(body).as("enabled module %s", k).contains("\"" + k + "\""));
        disabled.forEach(k -> assertThat(body).as("disabled module %s", k).doesNotContain("\"" + k + "\""));
    }

    @Test
    void theSyncRouteAndTheAdminRouteReturnTheSameThing() throws Exception {
        // One rule, two doors. If these ever diverge, the Admin page stops being evidence of what
        // the phone actually has — which is its entire purpose.
        String viaSync = taxonomy();
        String viaAdmin = mvc.perform(get("/api/admin/client-sync").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(viaSync).isEqualTo(viaAdmin);
    }

    @Test
    void itNeedsAuthenticationButNotAdminRights() throws Exception {
        mvc.perform(get("/sync/clientTaxonomy")).andExpect(status().isUnauthorized());
        // ...and with an ordinary token it succeeds — a phone user has no reason to hold
        // tools.admin, which is why this isn't served from the /api/admin route.
        mvc.perform(get("/sync/clientTaxonomy").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }
}
