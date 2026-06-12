/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import static org.assertj.core.api.Assertions.assertThat;

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

    private interface DefaultMethodClient {

        default String greet(final String name) {
            return "Hello " + name;
        }
    }
}
