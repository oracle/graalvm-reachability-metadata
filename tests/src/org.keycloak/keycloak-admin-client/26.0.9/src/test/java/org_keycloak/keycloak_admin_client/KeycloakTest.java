/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_admin_client;

import java.net.URI;

import jakarta.ws.rs.client.WebTarget;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.spi.ResteasyClientClassicProvider;
import org.keycloak.admin.client.spi.ResteasyClientProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class KeycloakTest {
    @Test
    void createsDefaultProviderAndInstantiatesClientsWrapper() {
        Keycloak keycloak = Keycloak.getInstance("http://localhost:8180/", "master", "admin-cli", "access-token");

        try {
            CapturingClients clients = keycloak.clients("master", CapturingClients.class);

            assertThat(Keycloak.getClientProvider()).isInstanceOf(ResteasyClientClassicProvider.class);
            assertThat(clients.provider()).isSameAs(Keycloak.getClientProvider());
            assertThat(clients.target().getUri()).isEqualTo(URI.create("http://localhost:8180/"));
            assertThat(clients.realmName()).isEqualTo("master");
            assertThat(keycloak.isClosed()).isFalse();
        } finally {
            keycloak.close();
        }

        assertThat(keycloak.isClosed()).isTrue();
    }

    public static class CapturingClients {
        private final ResteasyClientProvider provider;
        private final WebTarget target;
        private final String realmName;

        public CapturingClients(ResteasyClientProvider provider, WebTarget target, String realmName) {
            this.provider = provider;
            this.target = target;
            this.realmName = realmName;
        }

        ResteasyClientProvider provider() {
            return provider;
        }

        WebTarget target() {
            return target;
        }

        String realmName() {
            return realmName;
        }
    }
}
