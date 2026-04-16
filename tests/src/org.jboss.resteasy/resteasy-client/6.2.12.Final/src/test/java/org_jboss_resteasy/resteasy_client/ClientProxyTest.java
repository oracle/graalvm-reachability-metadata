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
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

class ClientProxyTest {

    @Test
    void defaultMethodInvocationUsesSpecialLookupWithoutSecurityManager() {
        try (ResteasyClient client = new ResteasyClientBuilderImpl().build()) {
            final DefaultMethodClient proxy = client.target("http://localhost:8080").proxy(DefaultMethodClient.class);

            assertThat(proxy.greet("Resteasy")).isEqualTo("Hello Resteasy");
        }
    }

    @Test
    @DisabledIfSystemProperty(named = "java.vm.name", matches = ".*Substrate VM.*")
    @SuppressWarnings("removal")
    void defaultMethodInvocationUsesPrivilegedSpecialLookupWithSecurityManager() {
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new PermissiveSecurityManager());

        try (ResteasyClient client = new ResteasyClientBuilderImpl().build()) {
            final DefaultMethodClient proxy = client.target("http://localhost:8080").proxy(DefaultMethodClient.class);

            assertThat(proxy.greet("Resteasy")).isEqualTo("Hello Resteasy");
        } finally {
            System.setSecurityManager(previousSecurityManager);
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
