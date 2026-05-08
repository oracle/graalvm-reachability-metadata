/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_admin_client;

import javax.net.ssl.SSLContext;

import jakarta.ws.rs.client.ClientBuilder;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.ClientBuilderWrapper;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientBuilderWrapperTest {
    @Test
    void createsResteasyClientBuilderWithConfiguredSslContextAndDisabledTrustManager() throws Exception {
        SSLContext sslContext = SSLContext.getDefault();

        ClientBuilder clientBuilder = ClientBuilderWrapper.create(sslContext, true);

        assertThat(clientBuilder).isInstanceOf(ResteasyClientBuilder.class);
        ResteasyClientBuilder resteasyClientBuilder = (ResteasyClientBuilder) clientBuilder;
        assertThat(resteasyClientBuilder.getConnectionPoolSize()).isEqualTo(10);
        assertThat(resteasyClientBuilder.isTrustManagerDisabled()).isTrue();
    }
}
