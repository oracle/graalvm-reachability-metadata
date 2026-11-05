/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml_snakeyaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises SnakeYAML's type-description subsystem for binding typed deployment targets.
public class DeploymentPlanTypeDescriptionTest {
    @Test
    @Timeout(60)
    public void loadsTypedTargetListFromDeploymentPlan() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        TypeDescription planDescription = new TypeDescription(DeploymentPlan.class);
        planDescription.putListPropertyType("targets", DeploymentTarget.class);

        Constructor constructor = new Constructor(DeploymentPlan.class, loaderOptions);
        constructor.addTypeDescription(planDescription);
        Yaml yaml = new Yaml(constructor);
        String planText = """
                name: checkout
                targets:
                  - id: primary
                    region: us-east-1
                    weight: 100
                  - id: failover
                    region: eu-west-1
                    weight: 0
                """;

        DeploymentPlan plan = yaml.load(planText);

        assertEquals("checkout", plan.getName());
        assertEquals(List.of(
                new DeploymentTarget("primary", "us-east-1", 100),
                new DeploymentTarget("failover", "eu-west-1", 0)), plan.getTargets());
    }

    public static final class DeploymentPlan {
        private String name;
        private List<DeploymentTarget> targets;

        public DeploymentPlan() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<DeploymentTarget> getTargets() {
            return targets;
        }

        public void setTargets(List<DeploymentTarget> targets) {
            this.targets = targets;
        }
    }

    public static final class DeploymentTarget {
        private String id;
        private String region;
        private int weight;

        public DeploymentTarget() {
        }

        private DeploymentTarget(String id, String region, int weight) {
            this.id = id;
            this.region = region;
            this.weight = weight;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof DeploymentTarget that
                    && weight == that.weight
                    && id.equals(that.id)
                    && region.equals(that.region);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, region, weight);
        }
    }
}
