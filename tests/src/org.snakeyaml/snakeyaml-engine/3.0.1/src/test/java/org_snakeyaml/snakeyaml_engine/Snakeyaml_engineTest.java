/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_snakeyaml.snakeyaml_engine;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.YamlUnicodeReader;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.api.lowlevel.Present;
import org.snakeyaml.engine.v2.api.lowlevel.Serialize;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.env.EnvConfig;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.exceptions.DuplicateKeyException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.schema.FailsafeSchema;

public class Snakeyaml_engineTest {
    @Test
    void loadsStructuredDocumentWithCoreScalarsAndAliases() {
        String document = """
                name: SnakeYAML Engine
                enabled: true
                count: 3
                ratio: 1.5
                nothing: null
                tags: &tags [yaml, native, \"\uD83D\uDC0D\"]
                copy: *tags
                nested:
                  threshold: 42
                """;

        Map<Object, Object> loaded = asMap(new Load(LoadSettings.builder().build()).loadFromString(document));

        assertThat(loaded).containsEntry("name", "SnakeYAML Engine");
        assertThat(loaded).containsEntry("enabled", true);
        assertThat((Number) loaded.get("count")).hasToString("3");
        assertThat((Number) loaded.get("ratio")).isEqualTo(1.5d);
        assertThat(loaded).containsEntry("nothing", null);
        assertThat(asList(loaded.get("tags"))).containsExactly("yaml", "native", "\uD83D\uDC0D");
        assertThat(loaded.get("copy")).isSameAs(loaded.get("tags"));
        assertThat(asMap(loaded.get("nested"))).containsEntry("threshold", 42);
    }

    @Test
    void loadsMultipleDocumentsFromSingleStream() {
        String stream = """
                ---
                name: first
                ---
                [1, 2, 3]
                ---
                true
                """;

        Iterable<Object> loaded = new Load(LoadSettings.builder().build()).loadAllFromString(stream);
        List<Object> documents = StreamSupport.stream(loaded.spliterator(), false).collect(Collectors.toList());

        assertThat(documents).hasSize(3);
        assertThat(asMap(documents.get(0))).containsEntry("name", "first");
        assertThat(asList(documents.get(1))).containsExactly(1, 2, 3);
        assertThat(documents.get(2)).isEqualTo(true);
    }

    @Test
    void dumpsFlowStyleYamlWithExplicitDocumentMarkersAndLoadsItBack() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("name", "SnakeYAML Engine");
        original.put("features", Arrays.asList("load", "dump", "compose"));
        original.put("limits", Map.of("aliases", 5, "unicode", true));

        DumpSettings settings = DumpSettings.builder()
                .setDefaultFlowStyle(FlowStyle.FLOW)
                .setExplicitStart(true)
                .setExplicitEnd(true)
                .build();
        String dumped = new Dump(settings).dumpToString(original);

        assertThat(dumped).startsWith("---");
        assertThat(dumped).contains("{").contains("...");
        assertThat(asMap(new Load(LoadSettings.builder().build()).loadFromString(dumped))).isEqualTo(original);
    }

    @Test
    void composeExposesYamlNodeGraphTagsAndStyles() {
        String document = """
                numbers: [1, 2]
                message: hello
                """;

        Optional<Node> composed = new Compose(LoadSettings.builder().build()).composeString(document);

        assertThat(composed).isPresent();
        MappingNode root = (MappingNode) composed.get();
        assertThat(root.getNodeType()).isEqualTo(NodeType.MAPPING);
        assertThat(root.getTag()).isEqualTo(Tag.MAP);
        assertThat(root.getValue()).hasSize(2);

        NodeTuple numbersTuple = root.getValue().get(0);
        assertThat(((ScalarNode) numbersTuple.getKeyNode()).getValue()).isEqualTo("numbers");
        SequenceNode numbers = (SequenceNode) numbersTuple.getValueNode();
        assertThat(numbers.getTag()).isEqualTo(Tag.SEQ);
        assertThat(numbers.getFlowStyle()).isEqualTo(FlowStyle.FLOW);
        assertThat(numbers.getValue()).extracting(node -> ((ScalarNode) node).getValue()).containsExactly("1", "2");
        assertThat(numbers.getValue()).extracting(Node::getTag).containsExactly(Tag.INT, Tag.INT);

        NodeTuple messageTuple = root.getValue().get(1);
        ScalarNode message = (ScalarNode) messageTuple.getValueNode();
        assertThat(message.getValue()).isEqualTo("hello");
        assertThat(message.getScalarStyle()).isEqualTo(ScalarStyle.PLAIN);
    }

    @Test
    void parserEmitsExpectedEventsAndScalarValues() {
        String document = "key: [1, two]\n";

        List<Event> events = toList(new Parse(LoadSettings.builder().build()).parseString(document));
        List<Event.ID> ids = events.stream().map(Event::getEventId).collect(Collectors.toList());
        List<String> scalarValues = events.stream()
                .filter(ScalarEvent.class::isInstance)
                .map(ScalarEvent.class::cast)
                .map(ScalarEvent::getValue)
                .collect(Collectors.toList());

        assertThat(ids).containsExactly(
                Event.ID.StreamStart,
                Event.ID.DocumentStart,
                Event.ID.MappingStart,
                Event.ID.Scalar,
                Event.ID.SequenceStart,
                Event.ID.Scalar,
                Event.ID.Scalar,
                Event.ID.SequenceEnd,
                Event.ID.MappingEnd,
                Event.ID.DocumentEnd,
                Event.ID.StreamEnd);
        assertThat(scalarValues).containsExactly("key", "1", "two");
    }

    @Test
    void serializerAndPresenterRoundTripComposedNode() {
        String document = """
                greeting: hello
                items:
                - one
                - two
                """;
        Node node = new Compose(LoadSettings.builder().build()).composeString(document).orElseThrow();

        List<Event> events = new Serialize(DumpSettings.builder().build()).serializeOne(node);
        String presented = new Present(DumpSettings.builder().build()).emitToString(events.iterator());
        Map<Object, Object> loaded = asMap(new Load(LoadSettings.builder().build()).loadFromString(presented));

        assertThat(events).extracting(Event::getEventId).contains(
                Event.ID.StreamStart,
                Event.ID.DocumentStart,
                Event.ID.MappingStart,
                Event.ID.SequenceStart,
                Event.ID.StreamEnd);
        assertThat(loaded).containsEntry("greeting", "hello");
        assertThat(asList(loaded.get("items"))).containsExactly("one", "two");
    }

    @Test
    void rejectsDuplicateKeysWhenConfigured() {
        LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(false).build();

        assertThatThrownBy(() -> new Load(settings).loadFromString("a: 1\na: 2\n"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("found duplicate key a");
    }

    @Test
    void failsafeSchemaKeepsImplicitScalarsAsStrings() {
        LoadSettings settings = LoadSettings.builder().setSchema(new FailsafeSchema()).build();

        Map<Object, Object> loaded = asMap(new Load(settings).loadFromString("{truth: true, count: 7, empty: null}"));

        assertThat(loaded).containsEntry("truth", "true");
        assertThat(loaded).containsEntry("count", "7");
        assertThat(loaded).containsEntry("empty", "null");
    }

    @Test
    void constructsApplicationValuesForCustomTags() {
        Tag durationTag = new Tag("!duration");
        LoadSettings settings = LoadSettings.builder()
                .setTagConstructors(Map.of(durationTag, node -> Duration.parse(((ScalarNode) node).getValue())))
                .build();
        String document = "timeout: !duration PT5S\n";

        Map<Object, Object> loaded = asMap(new Load(settings).loadFromString(document));

        assertThat(loaded).containsEntry("timeout", Duration.ofSeconds(5));
    }

    @Test
    void resolvesEnvironmentVariableTagsWithConfiguredProvider() {
        EnvConfig envConfig = new EnvConfig() {
            @Override
            public Optional<String> getValueFor(String name, String separator, String value, String environmentValue) {
                if ("SERVICE_NAME".equals(name)) {
                    return Optional.of("catalog");
                }
                if ("SERVICE_HOST".equals(name) && ":-".equals(separator)) {
                    return Optional.of(value);
                }
                return Optional.empty();
            }
        };
        LoadSettings settings = LoadSettings.builder().setEnvConfig(Optional.of(envConfig)).build();
        String document = """
                service: !ENV_VARIABLE ${SERVICE_NAME}
                endpoint: !ENV_VARIABLE ${SERVICE_HOST:-localhost}
                """;

        Map<Object, Object> loaded = asMap(new Load(settings).loadFromString(document));

        assertThat(loaded).containsEntry("service", "catalog");
        assertThat(loaded).containsEntry("endpoint", "localhost");
    }

    @Test
    void readsBomEncodedUnicodeAndConstructsBinaryScalars() throws IOException {
        String document = "message: caf\u00E9\npayload: !!binary SGVsbG8=\n";

        Object loaded;
        try (YamlUnicodeReader reader = new YamlUnicodeReader(new ByteArrayInputStream(withUtf8Bom(document)))) {
            assertThat(reader.getEncoding()).isEqualTo(UTF_8);
            loaded = new Load(LoadSettings.builder().build()).loadFromReader(reader);
        }

        Map<Object, Object> map = asMap(loaded);
        assertThat(map).containsEntry("message", "caf\u00E9");
        assertThat((byte[]) map.get("payload")).containsExactly("Hello".getBytes(UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> asMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<Object, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Object>) value;
    }

    private static List<Event> toList(Iterable<Event> events) {
        List<Event> collected = new ArrayList<>();
        Iterator<Event> iterator = events.iterator();
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }
        return collected;
    }

    private static byte[] withUtf8Bom(String value) {
        byte[] content = value.getBytes(UTF_8);
        byte[] bytes = new byte[content.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(content, 0, bytes, 3, content.length);
        return bytes;
    }
}
