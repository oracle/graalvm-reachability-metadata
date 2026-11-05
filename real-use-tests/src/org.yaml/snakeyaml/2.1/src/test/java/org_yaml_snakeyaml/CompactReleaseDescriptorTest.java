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
import org.yaml.snakeyaml.extensions.compactnotation.CompactConstructor;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises SnakeYAML's compact-notation construction subsystem for release descriptors.
public class CompactReleaseDescriptorTest {
    @Test
    @Timeout(60)
    public void loadsCompactReleaseDescriptorUsedByDeploymentTooling() {
        String descriptorText = "%s(name=billing-api, region=us-east-1, tier=critical)"
                .formatted(ReleaseDescriptor.class.getName());

        Yaml yaml = new Yaml(new CompactConstructor());
        ReleaseDescriptor descriptor = yaml.load(descriptorText);

        assertEquals("billing-api", descriptor.getName());
        assertEquals("us-east-1", descriptor.getRegion());
        assertEquals("critical", descriptor.getTier());
    }

    public static final class ReleaseDescriptor {
        private String name;
        private String region;
        private String tier;

        public ReleaseDescriptor() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getTier() {
            return tier;
        }

        public void setTier(String tier) {
            this.tier = tier;
        }
    }
}
