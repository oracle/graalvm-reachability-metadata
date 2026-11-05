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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.representer.Representer;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises SnakeYAML's comment-processing subsystem for preserving deployment notes during edits.
public class ConfigurationCommentPreservationTest {
    @Test
    @Timeout(60)
    public void preservesOperationalCommentsWhenConfigurationIsRewritten() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setProcessComments(true);
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setProcessComments(true);

        Yaml yaml = new Yaml(
                new SafeConstructor(loaderOptions),
                new Representer(dumperOptions),
                dumperOptions,
                loaderOptions);
        String configurationText = """
                # Application deployment settings
                service:
                  name: billing-api # stable service identifier
                  replicas: 3
                # Rollout policy is reviewed by release engineering.
                rollout:
                  strategy: canary
                """;

        Node root = yaml.compose(new StringReader(configurationText));
        assertNotNull(root);
        MappingNode configuration = (MappingNode) root;
        Map<String, Node> fields = mappingFields(configuration);

        assertEquals("billing-api", scalarValue(mappingFields((MappingNode) fields.get("service"))
                .get("name")));
        assertEquals("Application deployment settings", commentText(
                keyNode(configuration, "service").getBlockComments()));
        MappingNode service = (MappingNode) fields.get("service");
        assertEquals("stable service identifier", commentText(mappingFields(service).get("name")
                .getInLineComments()));
        assertEquals("Rollout policy is reviewed by release engineering.", commentText(
                keyNode(configuration, "rollout").getBlockComments()));

        StringWriter rewritten = new StringWriter();
        yaml.serialize(root, rewritten);
        String rewrittenText = rewritten.toString();

        assertTrue(rewrittenText.contains("# Application deployment settings"));
        assertTrue(rewrittenText.contains("name: billing-api # stable service identifier"));
        assertTrue(rewrittenText.contains("# Rollout policy is reviewed by release engineering."));
        assertFalse(rewrittenText.isBlank());
    }

    private static Map<String, Node> mappingFields(MappingNode mapping) {
        Map<String, Node> fields = new LinkedHashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            fields.put(scalarValue(tuple.getKeyNode()), tuple.getValueNode());
        }
        return fields;
    }

    private static Node keyNode(MappingNode mapping, String key) {
        for (NodeTuple tuple : mapping.getValue()) {
            if (key.equals(scalarValue(tuple.getKeyNode()))) {
                return tuple.getKeyNode();
            }
        }
        return null;
    }

    private static String scalarValue(Node node) {
        return ((ScalarNode) node).getValue();
    }

    private static String commentText(Iterable<CommentLine> comments) {
        assertNotNull(comments);
        assertTrue(comments.iterator().hasNext());
        return comments.iterator().next().getValue().trim();
    }
}
