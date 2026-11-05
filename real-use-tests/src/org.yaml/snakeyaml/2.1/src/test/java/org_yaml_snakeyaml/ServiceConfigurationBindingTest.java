/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises SnakeYAML's typed JavaBean binding subsystem for application configuration.
public class ServiceConfigurationBindingTest {
    @Test
    @Timeout(60)
    public void loadsTypedConfigurationForApplicationUse() {
        String configurationText = """
                service:
                  name: billing-api
                  replicas: 3
                  regions:
                    - us-east-1
                    - eu-west-1
                featureFlags:
                  audit: true
                  retryLimit: 4
                """;

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(loaderOptions);

        DeploymentConfiguration configuration = yaml.loadAs(
                configurationText, DeploymentConfiguration.class);

        assertEquals("billing-api", configuration.getService().getName());
        assertEquals(3, configuration.getService().getReplicas());
        assertEquals(List.of("us-east-1", "eu-west-1"), configuration.getService().getRegions());
        assertEquals(true, configuration.getFeatureFlags().get("audit"));
        assertEquals(4, configuration.getFeatureFlags().get("retryLimit"));
    }

    public static final class DeploymentConfiguration {
        private ServiceConfiguration service;
        private Map<String, Object> featureFlags;

        public DeploymentConfiguration() {
        }

        public ServiceConfiguration getService() {
            return service;
        }

        public void setService(ServiceConfiguration service) {
            this.service = service;
        }

        public Map<String, Object> getFeatureFlags() {
            return featureFlags;
        }

        public void setFeatureFlags(Map<String, Object> featureFlags) {
            this.featureFlags = featureFlags;
        }
    }

    public static final class ServiceConfiguration {
        private String name;
        private int replicas;
        private List<String> regions;

        public ServiceConfiguration() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getReplicas() {
            return replicas;
        }

        public void setReplicas(int replicas) {
            this.replicas = replicas;
        }

        public List<String> getRegions() {
            return regions;
        }

        public void setRegions(List<String> regions) {
            this.regions = regions;
        }
    }
}
