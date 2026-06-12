/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_enterprise.jakarta_enterprise_cdi_api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.enterprise.util.AnnotationLiteral;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsTest {
    @Test
    void annotationLiteralDiscoversDeclaredMembers() {
        final NamedQualifier qualifier = new NamedQualifierLiteral("without-security-manager");

        assertThat(qualifier.annotationType()).isEqualTo(NamedQualifier.class);
        assertThat(qualifier.toString()).contains("value=\"without-security-manager\"");
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface NamedQualifier {
        String value();
    }

    private static final class NamedQualifierLiteral extends AnnotationLiteral<NamedQualifier>
            implements NamedQualifier {
        private static final long serialVersionUID = 1L;

        private final String value;

        private NamedQualifierLiteral(final String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }
}
