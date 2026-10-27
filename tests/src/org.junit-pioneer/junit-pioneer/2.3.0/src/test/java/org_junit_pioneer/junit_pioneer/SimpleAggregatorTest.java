/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_pioneer.junit_pioneer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junitpioneer.jupiter.params.Aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleAggregatorTest {
    @ParameterizedTest
    @CsvSource("widgets, 7")
    void constructsAnObjectFromCsvArguments(@Aggregate LabelledAmount amount) {
        assertEquals("widgets", amount.label());
        assertEquals(7, amount.amount());
    }

    public static final class LabelledAmount {
        private final String label;
        private final int amount;

        public LabelledAmount(String label, int amount) {
            this.label = label;
            this.amount = amount;
        }

        public String label() {
            return label;
        }

        public int amount() {
            return amount;
        }
    }
}
