package com.nibras.pim.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Which version a GitHub release is for, from its tag or name. */
class ReleaseVersionTest {

    @Test
    void dateTagTakesTheVersionFromTheName() {
        assertEquals("1.3.1", InfoController.releaseVersion("release-2026-10-30", "Nibras PIM 1.3.1 — 2026-10-30"));
    }

    @Test
    void versionTagWins() {
        assertEquals("1.4.0", InfoController.releaseVersion("v1.4.0", "Nibras PIM 1.3.1"));
    }

    @Test
    void noVersionAnywhere() {
        assertNull(InfoController.releaseVersion("release-2026-10-19", "Nibras PIM — 2026-10-19"));
    }
}
