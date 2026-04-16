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
    void reflectionCompareReadsPrivateDeclaredFields() {
        final ReflectiveValue smaller = new ReflectiveValue("same", 1, 10);
        final ReflectiveValue larger = new ReflectiveValue("same", 2, 99);

        final int comparison = CompareToBuilder.reflectionCompare(smaller, larger);

        assertThat(comparison).isNegative();
    }

    @Test
    void reflectionCompareCanIncludeTransientAndInheritedFields() {
        final ReflectiveChild smaller = new ReflectiveChild(1, "same", 5);
        final ReflectiveChild larger = new ReflectiveChild(1, "same", 7);

        final int comparison = CompareToBuilder.reflectionCompare(
                smaller,
                larger,
                true,
                ReflectiveParent.class
        );

        assertThat(comparison).isNegative();
    }

    private static class ReflectiveParent {
        private final int inheritedOrder;

        private ReflectiveParent(final int inheritedOrder) {
            this.inheritedOrder = inheritedOrder;
        }
    }

    private static final class ReflectiveChild extends ReflectiveParent {
        private final String stableName;
        private transient int transientRank;

        private ReflectiveChild(final int inheritedOrder, final String stableName, final int transientRank) {
            super(inheritedOrder);
            this.stableName = stableName;
            this.transientRank = transientRank;
        }
    }

    private static final class ReflectiveValue {
        private static final int IGNORED_STATIC = 42;

        private final String stableName;
        private final int priority;
        private transient int ignoredTransient;

        private ReflectiveValue(final String stableName, final int priority, final int ignoredTransient) {
            this.stableName = stableName;
            this.priority = priority;
            this.ignoredTransient = ignoredTransient;
        }
    }
}
