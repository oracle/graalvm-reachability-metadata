/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_vault_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.core.VaultTemplate;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

/** Exercises Vault config-data loading against a development-mode Vault server. */
public class VaultConfigDataLoaderTest {
    private static final String ROOT_TOKEN = "native-image-test-token";
    private static final String SECRET_VALUE = "loaded-from-vault";
    private static final DockerImageName VAULT_IMAGE = DockerImageName.parse("hashicorp/vault:1.20.4");

    @Test
    @Timeout(value = 55, unit = TimeUnit.SECONDS)
    void loadsKvPropertiesAndRegistersVaultInfrastructure() {
        try (VaultContainer<?> vault = new VaultContainer<>(VAULT_IMAGE)
                .withVaultToken(ROOT_TOKEN)
                .withInitCommand("kv put secret/vault-config-loader greeting=" + SECRET_VALUE)
                .withStartupTimeout(Duration.ofSeconds(30))) {
            vault.start();

            SpringApplication application = new SpringApplication(TestApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);

            try (ConfigurableApplicationContext context = application.run(
                    "--spring.application.name=vault-config-loader",
                    "--spring.config.import=vault://",
                    "--spring.cloud.vault.uri=" + vault.getHttpHostAddress(),
                    "--spring.cloud.vault.authentication=TOKEN",
                    "--spring.cloud.vault.token=" + ROOT_TOKEN,
                    "--spring.cloud.vault.reactive.enabled=false",
                    "--spring.cloud.vault.config.lifecycle.enabled=false",
                    "--spring.cloud.vault.connection-timeout=10000",
                    "--spring.cloud.vault.read-timeout=10000")) {
                assertThat(context.getEnvironment().getProperty("greeting")).isEqualTo(SECRET_VALUE);
                assertThat(context.getBean(VaultTemplate.class)).isNotNull();
                assertThat(context.getBean(SessionManager.class)).isNotNull();
            }
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {}
}
