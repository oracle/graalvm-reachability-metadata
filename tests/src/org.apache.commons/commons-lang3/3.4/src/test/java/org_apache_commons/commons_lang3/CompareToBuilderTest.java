/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;

public class CompareToBuilderTest {

    @Test
    public void reflectionCompareOrdersObjectsUsingPrivateFields() {
        OrderedRecord lower = new OrderedRecord("alpha", 10);
        OrderedRecord higher = new OrderedRecord("alpha", 20);
        OrderedRecord matching = new OrderedRecord("alpha", 10);

        assertThat(CompareToBuilder.reflectionCompare(lower, higher)).isLessThan(0);
        assertThat(CompareToBuilder.reflectionCompare(higher, lower)).isGreaterThan(0);
        assertThat(CompareToBuilder.reflectionCompare(lower, matching)).isZero();
    }

    private static final class OrderedRecord {
        private final String group;
        private final int priority;

        private OrderedRecord(String group, int priority) {
            this.group = group;
            this.priority = priority;
        }
    }
}
