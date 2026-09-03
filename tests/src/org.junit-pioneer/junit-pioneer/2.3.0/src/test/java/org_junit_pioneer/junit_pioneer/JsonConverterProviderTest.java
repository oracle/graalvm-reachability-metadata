/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_pioneer.junit_pioneer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junitpioneer.jupiter.json.JsonSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonConverterProviderTest {
    @ParameterizedTest
    @JsonSource("\"inline-value\"")
    void parsesInlineJsonWithTheAvailableConverter(String value) {
        assertEquals("inline-value", value);
    }
}
