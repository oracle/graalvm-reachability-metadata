/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_enterprise.jakarta_enterprise_cdi_api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.Permission;

import javax.enterprise.util.AnnotationLiteral;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsTest {
    @Test
    @SuppressWarnings("removal")
    void annotationLiteralDiscoversDeclaredMembersWithoutSecurityManager() {
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final boolean securityManagerCleared = clearSecurityManagerIfPresent();

        try {
            final NamedQualifier qualifier = new NamedQualifierLiteral("without-security-manager");

            assertThat(qualifier.annotationType()).isEqualTo(NamedQualifier.class);
            assertThat(qualifier.toString()).contains("value=\"without-security-manager\"");
        } finally {
            if (securityManagerCleared) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @Test
    @SuppressWarnings("removal")
    void annotationLiteralDiscoversDeclaredMembersWithSecurityManager() {
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final PermissiveSecurityManager securityManager = new PermissiveSecurityManager();
        final boolean securityManagerInstalled = installSecurityManagerIfSupported(securityManager);

        try {
            final NamedQualifier qualifier = new NamedQualifierLiteral("with-security-manager");

            assertThat(qualifier.annotationType()).isEqualTo(NamedQualifier.class);
            assertThat(qualifier.toString()).contains("value=\"with-security-manager\"");
            if (securityManagerInstalled) {
                assertThat(System.getSecurityManager()).isSameAs(securityManager);
            }
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @SuppressWarnings("removal")
    private static boolean clearSecurityManagerIfPresent() {
        if (System.getSecurityManager() == null) {
            return false;
        }
        try {
            System.setSecurityManager(null);
            return System.getSecurityManager() == null;
        } catch (final UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported(final SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (final UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
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

    @SuppressWarnings("removal")
    private static final class PermissiveSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(final Permission permission) {
        }
    }
}
