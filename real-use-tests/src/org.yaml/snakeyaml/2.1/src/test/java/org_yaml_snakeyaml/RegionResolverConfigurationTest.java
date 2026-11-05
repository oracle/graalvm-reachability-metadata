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
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Exercises SnakeYAML's implicit-tag resolution subsystem for deployment regions.
public class RegionResolverConfigurationTest {
    @Test
    @Timeout(60)
    public void resolvesUnquotedRegionsIntoApplicationValues() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new RegionConstructor(loaderOptions));
        yaml.addImplicitResolver(
                RegionConstructor.REGION_TAG,
                Pattern.compile("[a-z]{2}-[a-z]+-[0-9]+"),
                "ue");

        String configurationText = """
                service: billing-api
                primaryRegion: us-east-1
                failoverRegions:
                  - eu-west-1
                  - us-west-2
                """;

        Map<?, ?> configuration = assertInstanceOf(Map.class, yaml.load(configurationText));
        Region primaryRegion = assertInstanceOf(Region.class, configuration.get("primaryRegion"));
        List<?> failoverRegions = assertInstanceOf(List.class, configuration.get("failoverRegions"));

        assertEquals("billing-api", configuration.get("service"));
        assertEquals("us-east-1", primaryRegion.value());
        assertEquals(
                List.of("eu-west-1", "us-west-2"),
                failoverRegions.stream()
                        .map(Region.class::cast)
                        .map(Region::value)
                        .toList());
    }

    private static final class RegionConstructor extends SafeConstructor {
        private static final Tag REGION_TAG = new Tag("!deployment-region");

        private RegionConstructor(LoaderOptions loaderOptions) {
            super(loaderOptions);
            yamlConstructors.put(REGION_TAG, new ConstructRegion());
        }

        private static final class ConstructRegion extends AbstractConstruct {
            @Override
            public Object construct(Node node) {
                ScalarNode scalar = (ScalarNode) node;
                return new Region(scalar.getValue());
            }
        }
    }

    private record Region(String value) {
    }
}
