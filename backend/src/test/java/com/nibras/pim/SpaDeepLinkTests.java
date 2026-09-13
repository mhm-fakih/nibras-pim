package com.nibras.pim;

import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deep links into the SPA must return the React shell, not a 404.
 *
 * <p>Guarding a regression that had a specific cause: {@code spring.mvc.problemdetails.enabled}
 * makes Spring resolve {@link org.springframework.web.servlet.resource.NoResourceFoundException}
 * into an RFC 7807 body and return it directly, so an unmatched path stopped reaching the
 * container's {@code /error} dispatch that {@code SpaController} relies on. Single-segment routes
 * kept working — {@code SpaController}'s own mapping catches those before any resource lookup — so
 * the breakage only showed on a two-segment route, and only when opened directly rather than
 * navigated to inside the app. {@code /read/planners/1231} in a new tab returned
 * <em>"No static resource read/planners/1231."</em>
 *
 * <p>The other half matters just as much: a genuinely missing backend path must keep its
 * machine-readable 404. Handing an HTML page to a JSON client would trade one silent failure for
 * another.
 */
class SpaDeepLinkTests extends AuthTestSupport {

    private static final String SHELL_MARKER = "<div id=\"root\"";

    private String body(String path) throws Exception {
        return mvc.perform(get(path).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void multiSegmentRoutesGetTheReactShell() throws Exception {
        // The exact link that was reported broken, plus the shapes around it.
        assertThat(body("/read/planners/1231")).contains(SHELL_MARKER);
        assertThat(body("/read/journals/7")).contains(SHELL_MARKER);
        assertThat(body("/dashboards/2/view")).contains(SHELL_MARKER);
    }

    @Test
    void singleSegmentRoutesStillWork() throws Exception {
        assertThat(body("/tasks")).contains(SHELL_MARKER);
        assertThat(body("/")).contains(SHELL_MARKER);
    }

    @Test
    void theShellCarriesABaseHrefSoAssetsResolve() throws Exception {
        // One WAR is deployed under several Tomcat contexts, and the shell is rewritten per request
        // to say which. A deep link served from the wrong place would load its assets from the
        // wrong place too.
        assertThat(body("/read/planners/1231")).contains("<base href=\"/\">");
    }

    @Test
    void missingBackendPathsNeverGetTheShell() throws Exception {
        // The exact status varies — security rejects an unknown /api path before MVC sees it — but
        // the invariant is the one that matters: a backend path must never be answered with HTML a
        // JSON client cannot parse.
        for (String path : new String[] { "/api/no-such-endpoint", "/sync/exportJsonNope/deeper" }) {
            var res = mvc.perform(get(path).header("Authorization", bearer())).andReturn().getResponse();
            assertThat(res.getStatus()).describedAs(path).isGreaterThanOrEqualTo(400);
            assertThat(res.getContentAsString()).describedAs(path).doesNotContain(SHELL_MARKER);
        }
    }

    @Test
    void missingAssetsAreStillMissing() throws Exception {
        // A path with a dot is a file that should exist. Answering with the shell would turn every
        // broken script tag into a page of HTML parsed as JavaScript.
        mvc.perform(get("/assets/index-deadbeef.js")).andExpect(status().isNotFound());
    }
}
