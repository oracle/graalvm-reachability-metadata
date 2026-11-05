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
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Exercises SnakeYAML's node-composition subsystem for validating configuration shape before loading it.
public class ConfigurationNodeInspectionTest {
    @Test
    @Timeout(60)
    public void inspectsConfigurationShapeBeforeApplicationLoadsIt() {
        String configurationText = """
                service:
                  name: billing-api
                  replicas: 3
                  regions: [us-east-1, eu-west-1]
                featureFlags:
                  audit: true
                """;

        Yaml yaml = new Yaml();
        Node root = yaml.compose(new StringReader(configurationText));
        MappingNode configuration = assertInstanceOf(MappingNode.class, root);
        Map<String, Node> fields = mappingFields(configuration);

        assertEquals(Tag.MAP, root.getTag());
        MappingNode service = assertInstanceOf(MappingNode.class, fields.get("service"));
        Map<String, Node> serviceFields = mappingFields(service);
        assertEquals("billing-api", scalarValue(serviceFields.get("name")));
        assertEquals(Tag.INT, serviceFields.get("replicas").getTag());
        assertEquals("3", scalarValue(serviceFields.get("replicas")));

        SequenceNode regions = assertInstanceOf(SequenceNode.class, serviceFields.get("regions"));
        assertEquals(Tag.SEQ, regions.getTag());
        assertEquals(
                List.of("us-east-1", "eu-west-1"),
                regions.getValue().stream().map(this::scalarValue).toList());

        MappingNode featureFlags = assertInstanceOf(
                MappingNode.class, fields.get("featureFlags"));
        assertEquals(Tag.BOOL, mappingFields(featureFlags).get("audit").getTag());
        assertEquals("true", scalarValue(mappingFields(featureFlags).get("audit")));
    }

    private Map<String, Node> mappingFields(MappingNode mapping) {
        Map<String, Node> fields = new LinkedHashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            ScalarNode key = assertInstanceOf(ScalarNode.class, tuple.getKeyNode());
            fields.put(key.getValue(), tuple.getValueNode());
        }
        return fields;
    }

    private String scalarValue(Node node) {
        return assertInstanceOf(ScalarNode.class, node).getValue();
    }
}
