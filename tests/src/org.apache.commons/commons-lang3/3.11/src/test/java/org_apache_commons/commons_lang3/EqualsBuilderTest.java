/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

public class EqualsBuilderTest {

    @Test
    void reflectionEqualsReadsPrivateDeclaredFields() {
        final ReflectiveValue baseline = new ReflectiveValue("shared", 7, 1);
        final ReflectiveValue sameDeclaredFields = new ReflectiveValue("shared", 7, 99);
        final ReflectiveValue differentDeclaredFields = new ReflectiveValue("shared", 8, 1);

        assertThat(EqualsBuilder.reflectionEquals(baseline, sameDeclaredFields)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(baseline, differentDeclaredFields)).isFalse();
    }

    @Test
    void reflectionEqualsCanIncludeTransientAndInheritedFields() {
        final ReflectiveChild differentTransient = new ReflectiveChild(1, "shared", 5);
        final ReflectiveChild sameExceptTransient = new ReflectiveChild(1, "shared", 8);
        final ReflectiveChild differentParent = new ReflectiveChild(2, "shared", 5);

        assertThat(EqualsBuilder.reflectionEquals(differentTransient, sameExceptTransient)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(differentTransient, sameExceptTransient, true)).isFalse();
        assertThat(EqualsBuilder.reflectionEquals(
                differentTransient,
                differentParent,
                true,
                ReflectiveParent.class
        )).isFalse();
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
