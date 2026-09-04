/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_vault_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.cloud.vault.config.VaultConfigDataLoader;
import org.springframework.cloud.vault.config.VaultConfigLocation;
import org.springframework.cloud.vault.config.VaultProperties;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.authentication.SessionManager;

public class VaultConfigDataLoaderTest {

    private static final String VAULT_IMAGE = "hashicorp/vault:1.21.1";

    private static final String ROOT_TOKEN = "test-root-token";

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration CONTAINER_START_TIMEOUT = Duration.ofSeconds(20);

    @Test
    @Timeout(value = 59)
    void loadsConfigurationUsingLifecycleAwareTokenAuthentication() throws Exception {
        int port = findAvailablePort();
        String containerName = "spring-cloud-vault-config-" + UUID.randomUUID();
        boolean containerStartAttempted = false;

        try {
            containerStartAttempted = true;
            startVault(containerName, port);
            waitUntilVaultIsReady(containerName);
            writeSecret(containerName);

            VaultProperties properties = vaultProperties(port);
            DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
            bootstrapContext.register(VaultProperties.class, ignored -> properties);
            DeferredLogs deferredLogs = new DeferredLogs();
            VaultConfigDataLoader loader = new VaultConfigDataLoader(deferredLogs);

            try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
                applicationContext.refresh();
                try {
                    ConfigData configData = loader.load(new TestConfigDataLoaderContext(bootstrapContext),
                            new VaultConfigLocation("secret/config-test", false));
                    PropertySource<?> propertySource = configData.getPropertySources().getFirst();
                    SessionManager sessionManager = bootstrapContext.get(SessionManager.class);

                    assertThat(sessionManager).isInstanceOf(LifecycleAwareSessionManager.class);
                    assertThat(configData.getPropertySources()).hasSize(1);
                    assertThat(propertySource.getProperty("message")).isEqualTo("loaded-from-vault");
                    assertThat(propertySource.getProperty("environment")).isEqualTo("integration-test");
                } finally {
                    bootstrapContext.close(applicationContext);
                }
            } finally {
                deferredLogs.switchOverAll();
            }
        } finally {
            if (containerStartAttempted) {
                removeContainer(containerName);
            }
        }
    }

    private static VaultProperties vaultProperties(int port) {
        VaultProperties properties = new VaultProperties();
        properties.setUri("http://127.0.0.1:" + port);
        properties.setScheme("http");
        properties.setAuthentication(VaultProperties.AuthenticationMethod.TOKEN);
        properties.setToken(ROOT_TOKEN);
        properties.setConnectionTimeout(10_000);
        properties.setReadTimeout(10_000);
        properties.getReactive().setEnabled(false);
        properties.getConfig().getLifecycle().setEnabled(false);
        properties.getSession().getLifecycle().setEnabled(true);
        return properties;
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void startVault(String containerName, int port) throws IOException, InterruptedException {
        runCommand(CONTAINER_START_TIMEOUT, "docker", "run", "--detach", "--rm", "--name", containerName,
                "--publish", "127.0.0.1:" + port + ":8200", "--cap-add=IPC_LOCK",
                "--env", "VAULT_DEV_ROOT_TOKEN_ID=" + ROOT_TOKEN,
                "--env", "VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200", VAULT_IMAGE, "server", "-dev");
    }

    private static void removeContainer(String containerName) throws IOException, InterruptedException {
        CommandResult result = executeCommand("docker", "rm", "--force", containerName);
        if (result.exitCode() != 0 && !result.output().contains("No such container")) {
            throw new IllegalStateException("Could not remove Vault container: " + result.output());
        }
    }

    private static void waitUntilVaultIsReady(String containerName) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        CommandResult result = null;

        do {
            result = executeCommand("docker", "exec", "--env", "VAULT_ADDR=http://127.0.0.1:8200",
                    containerName, "vault", "status");
            if (result.exitCode() == 0) {
                return;
            }
            Thread.sleep(200);
        } while (System.nanoTime() < deadline);

        throw new IllegalStateException("Vault did not become ready: " + result.output());
    }

    private static void writeSecret(String containerName) throws IOException, InterruptedException {
        runCommand("docker", "exec", "--env", "VAULT_ADDR=http://127.0.0.1:8200",
                "--env", "VAULT_TOKEN=" + ROOT_TOKEN, containerName, "vault", "kv", "put",
                "secret/config-test", "message=loaded-from-vault", "environment=integration-test");
    }

    private static void runCommand(String... command) throws IOException, InterruptedException {
        runCommand(COMMAND_TIMEOUT, command);
    }

    private static void runCommand(Duration timeout, String... command) throws IOException, InterruptedException {
        CommandResult result = executeCommand(timeout, command);
        if (result.exitCode() != 0) {
            throw new IllegalStateException("Command failed with exit code " + result.exitCode() + ": "
                    + result.output());
        }
    }

    private static CommandResult executeCommand(String... command) throws IOException, InterruptedException {
        return executeCommand(COMMAND_TIMEOUT, command);
    }

    private static CommandResult executeCommand(Duration timeout, String... command)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            throw new IllegalStateException("Command timed out after " + timeout + ": "
                    + String.join(" ", command));
        }
        try (InputStream input = process.getInputStream()) {
            String output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), output);
        }
    }

    private static final class TestConfigDataLoaderContext implements ConfigDataLoaderContext {

        private final ConfigurableBootstrapContext bootstrapContext;

        private TestConfigDataLoaderContext(ConfigurableBootstrapContext bootstrapContext) {
            this.bootstrapContext = bootstrapContext;
        }

        @Override
        public ConfigurableBootstrapContext getBootstrapContext() {
            return this.bootstrapContext;
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}
