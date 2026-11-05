/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml_snakeyaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises SnakeYAML's JavaBean representation subsystem for exporting deployment configuration.
public class DeploymentConfigurationSerializationTest {
    @Test
    @Timeout(60)
    public void exportsTypedConfigurationForAnotherDeploymentService() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(dumperOptions);

        DeploymentConfiguration configuration = new DeploymentConfiguration(
                new ServiceConfiguration("billing-api", 3, List.of("us-east-1", "eu-west-1")));

        String exported = yaml.dumpAsMap(configuration);
        assertEquals("service:\n  name: billing-api\n  regions:\n  - us-east-1\n  - eu-west-1\n  replicas: 3\n", exported);

        DeploymentConfiguration reloaded = yaml.loadAs(exported, DeploymentConfiguration.class);
        assertEquals(configuration, reloaded);
    }

    public static final class DeploymentConfiguration {
        private ServiceConfiguration service;

        public DeploymentConfiguration() {
        }

        private DeploymentConfiguration(ServiceConfiguration service) {
            this.service = service;
        }

        public ServiceConfiguration getService() {
            return service;
        }

        public void setService(ServiceConfiguration service) {
            this.service = service;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof DeploymentConfiguration that && service.equals(that.service);
        }

        @Override
        public int hashCode() {
            return service.hashCode();
        }
    }

    public static final class ServiceConfiguration {
        private String name;
        private int replicas;
        private List<String> regions;

        public ServiceConfiguration() {
        }

        private ServiceConfiguration(String name, int replicas, List<String> regions) {
            this.name = name;
            this.replicas = replicas;
            this.regions = regions;
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

        @Override
        public boolean equals(Object other) {
            return other instanceof ServiceConfiguration that
                    && replicas == that.replicas
                    && name.equals(that.name)
                    && regions.equals(that.regions);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, replicas, regions);
        }
    }
}
