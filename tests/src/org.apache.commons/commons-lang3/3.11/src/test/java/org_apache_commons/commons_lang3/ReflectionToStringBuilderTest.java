/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.junit.jupiter.api.Test;

public class ReflectionToStringBuilderTest {

    @Test
    void reflectionToStringReadsPrivateDeclaredFields() {
        final ReflectiveValue value = new ReflectiveValue("commons-lang", 7, 99);

        final String description = ReflectionToStringBuilder.toString(value, ToStringStyle.NO_CLASS_NAME_STYLE);

        assertThat(description)
                .contains("priority=7", "stableName=commons-lang")
                .doesNotContain("IGNORED_STATIC", "ignoredTransient");
    }

    @Test
    void reflectionToStringCanIncludeInheritedTransientAndStaticFields() {
        final ReflectiveChild value = new ReflectiveChild(1, "shared", 5);

        final String description = ReflectionToStringBuilder.toString(
                value,
                ToStringStyle.NO_CLASS_NAME_STYLE,
                true,
                true,
                ReflectiveParent.class
        );

        assertThat(description)
                .contains(
                        "DECLARED_STATIC_LABEL=child-type",
                        "inheritedOrder=1",
                        "stableName=shared",
                        "transientRank=5"
                );
    }

    private static class ReflectiveParent {
        private final int inheritedOrder;

        private ReflectiveParent(final int inheritedOrder) {
            this.inheritedOrder = inheritedOrder;
        }
    }

    private static final class ReflectiveChild extends ReflectiveParent {
        private static final String DECLARED_STATIC_LABEL = "child-type";

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
