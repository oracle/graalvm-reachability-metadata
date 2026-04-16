/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

public class HashCodeBuilderTest {

    @Test
    void reflectionHashCodeReadsPrivateDeclaredFields() {
        final ReflectiveValue value = new ReflectiveValue("shared", 7, 99);

        final int hashCode = HashCodeBuilder.reflectionHashCode(value);

        assertThat(hashCode).isEqualTo(new HashCodeBuilder()
                .append(7)
                .append("shared")
                .toHashCode());
    }

    @Test
    void reflectionHashCodeCanIncludeTransientAndInheritedFields() {
        final ReflectiveChild value = new ReflectiveChild(1, "shared", 5);

        final int hashCode = HashCodeBuilder.reflectionHashCode(19, 39, value, true, ReflectiveParent.class);

        assertThat(hashCode).isEqualTo(new HashCodeBuilder(19, 39)
                .append("shared")
                .append(5)
                .append(1)
                .toHashCode());
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

        private final int priority;
        private final String stableName;
        private transient int ignoredTransient;

        private ReflectiveValue(final String stableName, final int priority, final int ignoredTransient) {
            this.priority = priority;
            this.stableName = stableName;
            this.ignoredTransient = ignoredTransient;
        }
    }
}
