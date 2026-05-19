package dev.bwdesigngroup.prometheus.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke test that verifies the JUnit 5 + Spotless harness is wired up. Module-specific tests can
 * replace or extend this file.
 */
@DisplayName("Smoke Test")
class SmokeTest {
    @Test
    @DisplayName("JUnit 5 platform is wired")
    void junitPlatformIsWired() {
        assertEquals(4, 2 + 2);
    }
}
