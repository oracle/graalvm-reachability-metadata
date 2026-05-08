/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Permission;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class SecuritySupportTest {
    @Test
    void implicitConstructorConverterFindsDeclaredConstructorWithoutSecurityManager() {
        final ConstructorValue value = convert("plain.value", "plain", ConstructorValue.class);

        assertThat(value.value()).isEqualTo("plain");
    }

    @Test
    @SuppressWarnings("removal")
    void implicitConstructorConverterFindsDeclaredConstructorWithSecurityManager() {
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final PermissiveSecurityManager securityManager = new PermissiveSecurityManager();
        final boolean securityManagerInstalled = installSecurityManagerIfSupported(securityManager);

        try {
            final PrivilegedConstructorValue value = convert(
                    "privileged.value",
                    "privileged",
                    PrivilegedConstructorValue.class);

            assertThat(value.value()).isEqualTo("privileged");
            if (securityManagerInstalled) {
                assertThat(System.getSecurityManager()).isSameAs(securityManager);
            }
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    private static <T> T convert(final String key, final String rawValue, final Class<T> targetType) {
        final SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(key, rawValue), "security-support-test"))
                .build();

        return config.getValue(key, targetType);
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported(final SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    public static final class ConstructorValue {
        private final String value;

        private ConstructorValue(final String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    public static final class PrivilegedConstructorValue {
        private final String value;

        private PrivilegedConstructorValue(final String value) {
            this.value = value;
        }

        String value() {
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
