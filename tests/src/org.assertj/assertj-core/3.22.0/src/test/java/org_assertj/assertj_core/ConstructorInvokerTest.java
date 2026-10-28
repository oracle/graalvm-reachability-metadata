/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

public class ConstructorInvokerTest {
    @Test
    void equalityFailuresExposeComparisonValuesToJUnit() {
        AssertionFailedError failure = assertThrows(AssertionFailedError.class,
                () -> assertThat(4).as("remaining items").isEqualTo(9));
        assertEquals("9", failure.getExpected().getValue());
        assertEquals("4", failure.getActual().getValue());
        assertThat(failure).hasMessageContaining("remaining items")
                .hasMessageContaining("9").hasMessageContaining("4");
    }
}
