/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_enterprise.cdi_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import javax.enterprise.util.AnnotationLiteral;
import org.junit.jupiter.api.Test;

public class AnnotationLiteralTest {
    @Test
    void toStringReadsAnnotationMembersThroughLiteralImplementation() {
        BusinessQualifier qualifier = new BusinessQualifierLiteral(
                "orders",
                7,
                Map.class,
                new String[] {"fast", "stable"});

        String description = qualifier.toString();

        assertThat(qualifier.annotationType()).isEqualTo(BusinessQualifier.class);
        assertThat(description)
                .startsWith("@" + BusinessQualifier.class.getName() + "(")
                .contains("value=\"orders\"")
                .contains("priority=7")
                .contains("targetType=java.util.Map.class")
                .contains("tags={\"fast\", \"stable\"}")
                .endsWith(")");
    }

    @Test
    void equalsAndHashCodeUseAnnotationMemberValues() {
        BusinessQualifier first = new BusinessQualifierLiteral(
                "orders",
                7,
                Map.class,
                new String[] {"fast", "stable"});
        BusinessQualifier same = new BusinessQualifierLiteral(
                "orders",
                7,
                Map.class,
                new String[] {"fast", "stable"});
        BusinessQualifier different = new BusinessQualifierLiteral(
                "inventory",
                7,
                Map.class,
                new String[] {"fast", "stable"});

        assertThat(first)
                .isEqualTo(same)
                .hasSameHashCodeAs(same)
                .isNotEqualTo(different)
                .isNotEqualTo("orders");
    }

    public @interface BusinessQualifier {
        String value();

        int priority();

        Class<?> targetType();

        String[] tags();
    }

    private static final class BusinessQualifierLiteral extends AnnotationLiteral<BusinessQualifier>
            implements BusinessQualifier {
        private final String value;
        private final int priority;
        private final Class<?> targetType;
        private final String[] tags;

        private BusinessQualifierLiteral(String value, int priority, Class<?> targetType, String[] tags) {
            this.value = value;
            this.priority = priority;
            this.targetType = targetType;
            this.tags = tags;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public Class<?> targetType() {
            return targetType;
        }

        @Override
        public String[] tags() {
            return tags;
        }
    }
}
