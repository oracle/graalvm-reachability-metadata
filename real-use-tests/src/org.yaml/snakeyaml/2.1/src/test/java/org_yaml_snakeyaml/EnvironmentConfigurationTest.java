/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml_snakeyaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.env.EnvScalarConstructor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises SnakeYAML's environment-variable interpolation subsystem for deployment configuration.
public class EnvironmentConfigurationTest {
    @Test
    @Timeout(60)
    public void resolvesEnvironmentValuesAndDefaultsForDeployment() {
        Yaml yaml = new Yaml(new ApplicationEnvironmentConstructor(Map.of(
                "DEPLOY_REGION", "us-east-1",
                "RELEASE_LABEL", "release-42")));
        String configurationText = """
                service:
                  region: !ENV "${DEPLOY_REGION}"
                  endpoint: !ENV "${SERVICE_ENDPOINT:-https://service.internal}"
                  release: !ENV "${RELEASE_LABEL:?release-label-required}"
                """;

        Map<?, ?> configuration = yaml.load(configurationText);
        Map<?, ?> service = (Map<?, ?>) configuration.get("service");

        assertEquals("us-east-1", service.get("region"));
        assertEquals("https://service.internal", service.get("endpoint"));
        assertEquals("release-42", service.get("release"));
    }

    private static final class ApplicationEnvironmentConstructor extends EnvScalarConstructor {
        private final Map<String, String> environment;

        private ApplicationEnvironmentConstructor(Map<String, String> environment) {
            this.environment = environment;
        }

        @Override
        public String getEnv(String name) {
            return environment.get(name);
        }
    }
}
