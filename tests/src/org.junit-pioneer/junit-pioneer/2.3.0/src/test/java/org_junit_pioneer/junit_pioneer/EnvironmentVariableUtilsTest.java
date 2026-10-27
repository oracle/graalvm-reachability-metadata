/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_pioneer.junit_pioneer;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnvironmentVariableUtilsTest {
    private static final String VARIABLE = "JUNIT_PIONEER_REACHABILITY_TEST";

    @Test
    @SetEnvironmentVariable(key = VARIABLE, value = "configured")
    void setsAndRestoresAnEnvironmentVariable() {
        assertEquals("configured", System.getenv(VARIABLE));
    }
}
