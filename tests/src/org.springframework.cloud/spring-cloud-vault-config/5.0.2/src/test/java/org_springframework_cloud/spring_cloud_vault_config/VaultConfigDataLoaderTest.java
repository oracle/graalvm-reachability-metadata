/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_vault_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.cloud.vault.config.KeyValueSecretBackendMetadata;
import org.springframework.cloud.vault.config.SecretBackendMetadata;
import org.springframework.cloud.vault.config.VaultConfigDataLoader;
import org.springframework.cloud.vault.config.VaultConfigLocation;
import org.springframework.cloud.vault.config.VaultProperties;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.core.VaultTemplate;

/** Exercises Vault config-data loading against a local Vault-compatible HTTP endpoint. */
public class VaultConfigDataLoaderTest {
    private static final String ROOT_TOKEN = "native-image-test-token";
    private static final String SECRET_VALUE = "loaded-from-vault";
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);

    @Test
    @Timeout(value = 55, unit = TimeUnit.SECONDS)
    void loadsKvPropertiesAndRegistersVaultInfrastructure() throws Exception {
        HttpServer vaultServer = startVaultServer();

        try {
            URI vaultUri = URI.create("http://127.0.0.1:" + vaultServer.getAddress().getPort());
            VaultProperties properties = createVaultProperties(vaultUri);
            DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
            bootstrapContext.register(VaultProperties.class, ignored -> properties);
            DeferredLogs deferredLogs = new DeferredLogs();
            VaultConfigDataLoader loader = new VaultConfigDataLoader(deferredLogs);

            try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
                applicationContext.refresh();
                try {
                    ConfigDataLoaderContext loaderContext = () -> bootstrapContext;
                    SecretBackendMetadata backend =
                            KeyValueSecretBackendMetadata.create("secret", "vault-config-loader");
                    ConfigData configData = loader.load(loaderContext, new VaultConfigLocation(backend, false));
                    PropertySource<?> propertySource = configData.getPropertySources().get(0);
                    SessionManager sessionManager = bootstrapContext.get(SessionManager.class);

                    assertThat(configData.getPropertySources()).hasSize(1);
                    assertThat(propertySource.getProperty("greeting")).isEqualTo(SECRET_VALUE);
                    assertThat(bootstrapContext.get(VaultTemplate.class)).isNotNull();
                    assertThat(sessionManager).isInstanceOf(LifecycleAwareSessionManager.class);
                    assertThat(sessionManager.getSessionToken().getToken()).isEqualTo(ROOT_TOKEN);
                } finally {
                    bootstrapContext.close(applicationContext);
                }
            } finally {
                deferredLogs.switchOverAll();
            }
        } finally {
            vaultServer.stop(0);
        }
    }

    private static HttpServer startVaultServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", VaultConfigDataLoaderTest::handleVaultRequest);
        server.start();
        return server;
    }

    private static void handleVaultRequest(HttpExchange exchange) throws IOException {
        String response;
        if (exchange.getRequestURI().getPath().equals("/v1/auth/token/lookup-self")) {
            response = """
                    {"data":{"id":"native-image-test-token","renewable":false,"ttl":3600}}
                    """;
        } else if (exchange.getRequestURI().getPath().startsWith("/v1/secret/")) {
            response = """
                    {"data":{"greeting":"loaded-from-vault","data":{"greeting":"loaded-from-vault"},"metadata":{"version":1}}}
                    """;
        } else {
            response = "{}";
        }

        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(responseBytes);
        }
    }

    private static VaultProperties createVaultProperties(URI vaultUri) {
        VaultProperties properties = new VaultProperties();
        properties.setUri(vaultUri.toString());
        properties.setAuthentication(VaultProperties.AuthenticationMethod.TOKEN);
        properties.setToken(ROOT_TOKEN);
        properties.setConnectionTimeout((int) IO_TIMEOUT.toMillis());
        properties.setReadTimeout((int) IO_TIMEOUT.toMillis());
        properties.getReactive().setEnabled(false);
        properties.getConfig().getLifecycle().setEnabled(false);
        properties.getSession().getLifecycle().setEnabled(true);
        return properties;
    }

}
