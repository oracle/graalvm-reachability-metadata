/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Permission;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.junit.jupiter.api.Test;

public class ClientProxyTest {

    @Test
    void defaultMethodInvocationUsesSpecialLookupWithoutSecurityManager() {
        try (ResteasyClient client = new ResteasyClientBuilderImpl().build()) {
            final DefaultMethodClient proxy = client.target("http://localhost:8080").proxy(DefaultMethodClient.class);

            assertThat(proxy.greet("Resteasy")).isEqualTo("Hello Resteasy");
        }
    }

    @Test
    @SuppressWarnings("removal")
    void defaultMethodInvocationUsesPrivilegedSpecialLookupWhenSecurityManagerCanBeInstalled() {
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        boolean installedSecurityManager = false;
        try {
            try {
                System.setSecurityManager(new PermissiveSecurityManager());
                installedSecurityManager = true;
            } catch (final UnsupportedOperationException unsupportedOperationException) {
                assertThat(Runtime.version().feature())
                        .as(unsupportedOperationException.getMessage())
                        .isGreaterThanOrEqualTo(24);
            }

            try (ResteasyClient client = new ResteasyClientBuilderImpl().build()) {
                final DefaultMethodClient proxy = client.target("http://localhost:8080").proxy(DefaultMethodClient.class);

                assertThat(proxy.greet("Resteasy")).isEqualTo("Hello Resteasy");
            }
        } finally {
            if (installedSecurityManager) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    private interface DefaultMethodClient {

        default String greet(final String name) {
            return "Hello " + name;
        }
    }

    @SuppressWarnings("removal")
    private static final class PermissiveSecurityManager extends SecurityManager {

        @Override
        public void checkPermission(final Permission permission) {
        }
    }
}
