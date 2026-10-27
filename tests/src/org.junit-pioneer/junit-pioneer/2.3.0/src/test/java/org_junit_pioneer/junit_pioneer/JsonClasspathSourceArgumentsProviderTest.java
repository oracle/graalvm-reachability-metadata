/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_pioneer.junit_pioneer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junitpioneer.jupiter.json.JsonClasspathSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonClasspathSourceArgumentsProviderTest {
    @ParameterizedTest
    @JsonClasspathSource("org_junit_pioneer/junit_pioneer/classpath-values.json")
    void readsJsonValuesFromTheTestClasspath(String value) {
        assertEquals("classpath-value", value);
    }
}
