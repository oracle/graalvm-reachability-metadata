/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_pioneer.junit_pioneer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junitpioneer.jupiter.params.IntRangeSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RangeSourceArgumentsProviderTest {
    @ParameterizedTest
    @IntRangeSource(from = 12, to = 13)
    void constructsTheConfiguredIntegerRange(int value) {
        assertEquals(12, value);
    }
}
