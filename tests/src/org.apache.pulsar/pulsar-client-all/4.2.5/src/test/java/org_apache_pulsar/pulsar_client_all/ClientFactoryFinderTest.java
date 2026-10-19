/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.javax.ws.rs.client.Client;
import org.apache.pulsar.shade.javax.ws.rs.client.ClientBuilder;
import org.junit.jupiter.api.Test;

public class ClientFactoryFinderTest {
    private static final String PROVIDER =
            "org.apache.pulsar.shade.org.glassfish.jersey.client.JerseyClientBuilder";

    @Test
    void instantiatesConfiguredBuilderWithContextAndDefiningClassLoaders() {
        String key = ClientBuilder.JAXRS_DEFAULT_CLIENT_BUILDER_PROPERTY;
        String previous = System.getProperty(key);
        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        System.setProperty(key, PROVIDER);
        try {
            assertUsable(ClientBuilder.newBuilder());
            Thread.currentThread().setContextClassLoader(null);
            assertUsable(ClientBuilder.newBuilder());
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
            restoreProperty(key, previous);
        }
    }

    private static void assertUsable(ClientBuilder builder) {
        Client client = builder.build();
        try {
            assertThat(client.getConfiguration().getProperties()).isNotNull();
        } finally {
            client.close();
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
