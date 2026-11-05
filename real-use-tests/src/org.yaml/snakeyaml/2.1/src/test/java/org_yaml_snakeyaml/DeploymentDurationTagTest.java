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

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Exercises SnakeYAML's custom-tag construction subsystem for typed deployment timeouts.
public class DeploymentDurationTagTest {
    @Test
    @Timeout(60)
    public void loadsApplicationTimeoutsThroughCustomYamlTags() {
        String configurationText = """
                service: billing-api
                timeouts:
                  connect: !duration PT5S
                  request: !duration PT30S
                """;

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new DurationConstructor(loaderOptions));

        Map<?, ?> configuration = assertInstanceOf(Map.class, yaml.load(configurationText));
        Map<?, ?> timeouts = assertInstanceOf(Map.class, configuration.get("timeouts"));

        assertEquals("billing-api", configuration.get("service"));
        assertEquals(Duration.ofSeconds(5), timeouts.get("connect"));
        assertEquals(Duration.ofSeconds(30), timeouts.get("request"));
    }

    private static final class DurationConstructor extends SafeConstructor {
        private static final Tag DURATION_TAG = new Tag("!duration");

        private DurationConstructor(LoaderOptions loaderOptions) {
            super(loaderOptions);
            yamlConstructors.put(DURATION_TAG, new ConstructDuration());
        }

        private static final class ConstructDuration extends AbstractConstruct {
            @Override
            public Object construct(Node node) {
                ScalarNode scalar = (ScalarNode) node;
                return Duration.parse(scalar.getValue());
            }
        }
    }
}
