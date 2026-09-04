/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_vault_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
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

/** Exercises Vault config-data loading against a development-mode Vault server. */
public class VaultConfigDataLoaderTest {
    private static final String ROOT_TOKEN = "native-image-test-token";
    private static final String SECRET_VALUE = "loaded-from-vault";
    private static final String VAULT_IMAGE = "hashicorp/vault:1.20.4";
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);

    @Test
    @Timeout(value = 55, unit = TimeUnit.SECONDS)
    void loadsKvPropertiesAndRegistersVaultInfrastructure() throws Exception {
        String containerName = "graalvm-vault-config-" + ProcessHandle.current().pid();
        removeContainer(containerName);

        startContainer(containerName);

        try {
            URI vaultUri = waitForVault(containerName);
            putSecret(vaultUri);

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
            removeContainer(containerName);
        }
    }

    private static void startContainer(String containerName) throws Exception {
        Process startCommand = new ProcessBuilder(
                        "docker",
                        "run",
                        "--detach",
                        "--rm",
                        "--name",
                        containerName,
                        "--cap-add=IPC_LOCK",
                        "-e",
                        "VAULT_DEV_ROOT_TOKEN_ID=" + ROOT_TOKEN,
                        "-p",
                        "127.0.0.1::8200",
                        VAULT_IMAGE,
                        "server",
                        "-dev",
                        "-dev-listen-address=0.0.0.0:8200")
                .redirectErrorStream(true)
                .start();
        if (!startCommand.waitFor(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            startCommand.destroyForcibly();
            throw new IllegalStateException("Timed out while starting the Vault container");
        }
        String output = new String(startCommand.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (startCommand.exitValue() != 0) {
            throw new IllegalStateException("Could not start the Vault container: " + output);
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

    private static URI waitForVault(String containerName) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(IO_TIMEOUT).build();
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        Exception lastFailure = null;

        while (System.nanoTime() < deadline) {
            try {
                URI vaultUri = findVaultUri(containerName);
                HttpRequest request = HttpRequest.newBuilder(vaultUri.resolve("/v1/sys/health"))
                        .timeout(IO_TIMEOUT)
                        .GET()
                        .build();
                HttpResponse<Void> response =
                        client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return vaultUri;
                }
            } catch (IOException | InterruptedException | IllegalStateException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                lastFailure = e;
            }
            Thread.sleep(200);
        }

        throw new IllegalStateException("Vault did not become ready within " + STARTUP_TIMEOUT, lastFailure);
    }

    private static URI findVaultUri(String containerName) throws Exception {
        Process portCommand = new ProcessBuilder("docker", "port", containerName, "8200/tcp")
                .redirectErrorStream(true)
                .start();
        if (!portCommand.waitFor(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            portCommand.destroyForcibly();
            throw new IllegalStateException("Timed out while resolving the Vault container port");
        }
        String output = new String(portCommand.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (portCommand.exitValue() != 0 || output.isEmpty()) {
            throw new IllegalStateException("Vault container port is not available: " + output);
        }
        return URI.create("http://" + output.lines().findFirst().orElseThrow());
    }

    private static void putSecret(URI vaultUri) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(IO_TIMEOUT).build();
        String body = "{\"data\":{\"greeting\":\"" + SECRET_VALUE + "\"}}";
        HttpRequest request = HttpRequest.newBuilder(vaultUri.resolve("/v1/secret/data/vault-config-loader"))
                .timeout(IO_TIMEOUT)
                .header("X-Vault-Token", ROOT_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private static void removeContainer(String containerName) throws Exception {
        Process removeCommand = new ProcessBuilder("docker", "rm", "-f", containerName)
                .redirectErrorStream(true)
                .start();
        if (!removeCommand.waitFor(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            removeCommand.destroyForcibly();
            removeCommand.waitFor(IO_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
    }
}
