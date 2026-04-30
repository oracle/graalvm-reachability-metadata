/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.apt;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.tools.apt.DocumentationHelper;
import org.apache.camel.tools.apt.EndpointAnnotationProcessor;
import org.apache.camel.tools.apt.helper.CollectionStringBuffer;
import org.apache.camel.tools.apt.helper.EndpointHelper;
import org.apache.camel.tools.apt.helper.IOHelper;
import org.apache.camel.tools.apt.helper.JsonSchemaHelper;
import org.apache.camel.tools.apt.helper.Strings;
import org.apache.camel.tools.apt.model.ComponentModel;
import org.apache.camel.tools.apt.model.ComponentOption;
import org.apache.camel.tools.apt.model.EndpointOption;
import org.apache.camel.tools.apt.model.EndpointPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AptTest {
    @Test
    void stringAndCollectionHelpersHandleCommonAptFormattingCases() {
        assertThat(Strings.isNullOrEmpty(null)).isTrue();
        assertThat(Strings.isNullOrEmpty("")).isTrue();
        assertThat(Strings.isNullOrEmpty("null")).isTrue();
        assertThat(Strings.isNullOrEmpty("camel")).isFalse();
        assertThat(Strings.safeNull(null)).isEmpty();
        assertThat(Strings.safeNull("value")).isEqualTo("value");
        assertThat(Strings.getOrElse(null, "fallback")).isEqualTo("fallback");
        assertThat(Strings.getOrElse("present", "fallback")).isEqualTo("present");
        assertThat(Strings.after("demo:host:port", ":")).isEqualTo("host:port");
        assertThat(Strings.after("demo", ":")).isNull();
        assertThat(Strings.canonicalClassName("java.util.List<java.lang.String>")).isEqualTo("java.util.List");
        assertThat(Strings.doubleQuote("name")).isEqualTo("\"name\"");
        assertThat(Strings.singleQuote("name")).isEqualTo("'name'");
        assertThat(Strings.quote("name", "`")).isEqualTo("`name`");

        CollectionStringBuffer commaSeparated = new CollectionStringBuffer();
        commaSeparated.append("alpha");
        commaSeparated.append("beta");
        assertThat(commaSeparated.getSeparator()).isEqualTo(", ");
        assertThat(commaSeparated.toString()).isEqualTo("alpha, beta");

        commaSeparated.setSeparator(" | ");
        commaSeparated.append("gamma");
        assertThat(commaSeparated.toString()).isEqualTo("alpha, beta | gamma");

        CollectionStringBuffer htmlSeparated = new CollectionStringBuffer("<br/>");
        htmlSeparated.append("one");
        htmlSeparated.append("two");
        assertThat(htmlSeparated.toString()).isEqualTo("one<br/>two");
    }

    @Test
    void ioHelperReadsTextAndClosesAllResources() throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream("first\nsecond".getBytes(StandardCharsets.UTF_8));
        assertThat(IOHelper.loadText(stream, false)).isEqualTo("first\nsecond\n");

        RecordingCloseable first = new RecordingCloseable();
        RecordingCloseable second = new RecordingCloseable();
        IOHelper.close(first, second);
        assertThat(first.closed).isTrue();
        assertThat(second.closed).isTrue();
    }

    @Test
    void jsonSchemaHelperMapsJavaTypesAndSanitizesJavadoc() {
        assertThat(JsonSchemaHelper.getType(null, false)).isEqualTo("object");
        assertThat(JsonSchemaHelper.getType(URI.class.getName(), false)).isEqualTo("string");
        assertThat(JsonSchemaHelper.getType("java.io.File", false)).isEqualTo("string");
        assertThat(JsonSchemaHelper.getType("java.util.List<java.lang.String>", false)).isEqualTo("array");
        assertThat(JsonSchemaHelper.getType("java.lang.String", true)).isEqualTo("enum");
        assertThat(JsonSchemaHelper.getPrimitiveType("boolean")).isEqualTo("boolean");
        assertThat(JsonSchemaHelper.getPrimitiveType("Integer")).isEqualTo("integer");
        assertThat(JsonSchemaHelper.getPrimitiveType("double")).isEqualTo("number");
        assertThat(JsonSchemaHelper.getPrimitiveType("byte[]")).isEqualTo("string");
        assertThat(JsonSchemaHelper.getPrimitiveType("Object[]")).isEqualTo("array");
        assertThat(JsonSchemaHelper.getPrimitiveType("com.example.CustomType")).isNull();

        String javadoc = "<p>Uses {@link org.apache.camel.spi.Registry} values.</p>\n"
                + "Second sentence with punctuation!\n"
                + "@param ignored should not be included";
        assertThat(JsonSchemaHelper.sanitizeDescription(javadoc, false))
                .isEqualTo("Uses org.apache.camel.spi.Registry values. Second sentence with punctuation!");
        assertThat(JsonSchemaHelper.sanitizeDescription(javadoc, true))
                .isEqualTo("Uses org.apache.camel.spi.Registry values.");
        assertThat(JsonSchemaHelper.sanitizeDescription(null, false)).isNull();
    }

    @Test
    void jsonSchemaHelperWritesAndParsesPropertyMetadata() {
        String jsonProperty = JsonSchemaHelper.toJson(
                "mode",
                "parameter",
                Boolean.TRUE,
                "com.example.Mode",
                "AUTO",
                "Selects <b>mode</b>.\n@return ignored",
                Boolean.FALSE,
                "producer",
                "advanced",
                true,
                orderedSet("AUTO", "MANUAL"),
                false,
                orderedSet(),
                "header.",
                "headers",
                true);

        String json = "{\n"
                + "  \"properties\": {\n"
                + "    " + jsonProperty + "\n"
                + "  }\n"
                + "}";
        List<Map<String, String>> rows = JsonSchemaHelper.parseJsonSchema("properties", json, true);

        assertThat(rows).hasSize(1);
        Map<String, String> row = rows.get(0);
        assertThat(row.get("name")).isEqualTo("mode");
        assertThat(row.get("kind")).isEqualTo("parameter");
        assertThat(row.get("group")).isEqualTo("producer");
        assertThat(row.get("label")).isEqualTo("advanced");
        assertThat(row.get("required")).isEqualTo("true");
        assertThat(row.get("type")).isEqualTo("string");
        assertThat(row.get("javaType")).isEqualTo("com.example.Mode");
        assertThat(row.get("enum")).isEqualTo("AUTO,MANUAL");
        assertThat(row.get("optionalPrefix")).isEqualTo("header.");
        assertThat(row.get("prefix")).isEqualTo("headers");
        assertThat(row.get("multiValue")).isEqualTo("true");
        assertThat(row.get("deprecated")).isEqualTo("false");
        assertThat(row.get("defaultValue")).isEqualTo("AUTO");
        assertThat(row.get("description")).isEqualTo("Selects mode.");

        assertThat(JsonSchemaHelper.parseJsonSchema("missing", json, true)).isEmpty();
        assertThat(JsonSchemaHelper.parseJsonSchema("properties", null, true)).isEmpty();
    }

    @Test
    void modelObjectsExposeConstructorDataAndNameBasedIdentity() {
        ComponentModel componentModel = new ComponentModel("demo");
        componentModel.setExtendsScheme("file");
        componentModel.setSyntax("demo:path");
        componentModel.setAlternativeSyntax("demo:alt");
        componentModel.setJavaType("com.example.DemoComponent");
        componentModel.setTitle("Demo");
        componentModel.setDescription("Demo component");
        componentModel.setGroupId("com.example");
        componentModel.setArtifactId("demo-component");
        componentModel.setVersionId("1.0.0");
        componentModel.setLabel("testing");
        componentModel.setConsumerOnly(true);
        componentModel.setDeprecated(true);
        componentModel.setLenientProperties(true);

        assertThat(componentModel.getScheme()).isEqualTo("demo");
        assertThat(componentModel.getExtendsScheme()).isEqualTo("file");
        assertThat(componentModel.getSyntax()).isEqualTo("demo:path");
        assertThat(componentModel.getAlternativeSyntax()).isEqualTo("demo:alt");
        assertThat(componentModel.getJavaType()).isEqualTo("com.example.DemoComponent");
        assertThat(componentModel.getTitle()).isEqualTo("Demo");
        assertThat(componentModel.getDescription()).isEqualTo("Demo component");
        assertThat(componentModel.getGroupId()).isEqualTo("com.example");
        assertThat(componentModel.getArtifactId()).isEqualTo("demo-component");
        assertThat(componentModel.getVersionId()).isEqualTo("1.0.0");
        assertThat(componentModel.getLabel()).isEqualTo("testing");
        assertThat(componentModel.isConsumerOnly()).isTrue();
        assertThat(componentModel.isProducerOnly()).isFalse();
        assertThat(componentModel.isDeprecated()).isTrue();
        assertThat(componentModel.isLenientProperties()).isTrue();

        EndpointOption endpointOption = new EndpointOption("mode", "java.lang.String", "true", "auto", "use auto",
                "Mode to use", "header.", "headers", true, false, "producer", "advanced", true,
                orderedSet("auto", "manual"));
        assertThat(endpointOption.getName()).isEqualTo("mode");
        assertThat(endpointOption.getType()).isEqualTo("java.lang.String");
        assertThat(endpointOption.getRequired()).isEqualTo("true");
        assertThat(endpointOption.getDefaultValue()).isEqualTo("auto");
        assertThat(endpointOption.getDocumentation()).isEqualTo("Mode to use");
        assertThat(endpointOption.getDocumentationWithNotes()).isEqualTo("Mode to use. Default value notice: use auto");
        assertThat(endpointOption.getOptionalPrefix()).isEqualTo("header.");
        assertThat(endpointOption.getPrefix()).isEqualTo("headers");
        assertThat(endpointOption.isMultiValue()).isTrue();
        assertThat(endpointOption.isDeprecated()).isFalse();
        assertThat(endpointOption.getGroup()).isEqualTo("producer");
        assertThat(endpointOption.getLabel()).isEqualTo("advanced");
        assertThat(endpointOption.isEnumType()).isTrue();
        assertThat(endpointOption.getEnums()).containsExactly("auto", "manual");
        assertThat(endpointOption.getEnumValuesAsHtml()).isEqualTo("auto<br/>manual");
        assertThat(endpointOption).isEqualTo(new EndpointOption("mode", "int", "false", null, null,
                "Different metadata", null, null, false, true, "common", "common", false, orderedSet()));
        assertThat(endpointOption.hashCode()).isEqualTo("mode".hashCode());

        EndpointPath endpointPath = new EndpointPath("path", "java.lang.String", "true", null,
                "Path documentation", false, "common", "common", true, orderedSet("inbox", "outbox"));
        assertThat(endpointPath.getName()).isEqualTo("path");
        assertThat(endpointPath.getDocumentation()).isEqualTo("Path documentation");
        assertThat(endpointPath.getGroup()).isEqualTo("common");
        assertThat(endpointPath.getLabel()).isEqualTo("common");
        assertThat(endpointPath.getEnumValuesAsHtml()).isEqualTo("inbox<br/>outbox");
        assertThat(endpointPath).isEqualTo(new EndpointPath("path", "int", null, null,
                null, false, null, null, false, orderedSet()));

        ComponentOption componentOption = new ComponentOption("lazyStartProducer", "boolean", null, null,
                "false by default", "Whether producer startup is lazy", false, "producer", "advanced",
                false, orderedSet());
        assertThat(componentOption.getName()).isEqualTo("lazyStartProducer");
        assertThat(componentOption.getType()).isEqualTo("boolean");
        assertThat(componentOption.getDocumentationWithNotes())
                .isEqualTo("Whether producer startup is lazy. Default value notice: false by default");
        assertThat(componentOption.getLabel()).isEqualTo("advanced");
        assertThat(componentOption).isEqualTo(new ComponentOption("lazyStartProducer", "java.lang.String", "true",
                "x", null, "Other docs", true, "common", "common", false, orderedSet()));
    }

    @Test
    void endpointHelperDerivesGroupsFromLabels() {
        assertThat(EndpointHelper.labelAsGroupName(null, false, false)).isEqualTo("common");
        assertThat(EndpointHelper.labelAsGroupName("", true, false)).isEqualTo("consumer");
        assertThat(EndpointHelper.labelAsGroupName("", false, true)).isEqualTo("producer");
        assertThat(EndpointHelper.labelAsGroupName("consumer,advanced", false, false)).isEqualTo("consumer (advanced)");
        assertThat(EndpointHelper.labelAsGroupName("common,security", false, false)).isEqualTo("security");
        assertThat(EndpointHelper.labelAsGroupName("advanced", false, false)).isEqualTo("advanced");
    }

    @Test
    void endpointHelperSortsEndpointOptionsByDocumentedGroupPriorityAndName() {
        List<EndpointOption> options = Arrays.asList(
                endpointOption("zeta", "producer", "producer"),
                endpointOption("authentication", "security", "security"),
                endpointOption("timeout", "common (advanced)", "common,advanced"),
                endpointOption("beta", "common", "common"),
                endpointOption("alpha", "common", null),
                endpointOption("delayed", "consumer", "consumer"));
        Comparator<EndpointOption> comparator = EndpointHelper.createGroupAndLabelComparator();

        options.sort(comparator);

        assertThat(options)
                .extracting(EndpointOption::getName)
                .containsExactly("alpha", "beta", "timeout", "delayed", "zeta", "authentication");
    }

    @Test
    void endpointAnnotationProcessorCreatesCompleteComponentSchema() {
        ComponentModel component = new ComponentModel("demo");
        component.setExtendsScheme("file");
        component.setSyntax("demo:first/{second}");
        component.setAlternativeSyntax("demo:alt");
        component.setJavaType("com.example.DemoComponent");
        component.setTitle("Demo");
        component.setDescription("Demo endpoint component");
        component.setGroupId("com.example");
        component.setArtifactId("demo-component");
        component.setVersionId("1.0.0");
        component.setLabel("testing");
        component.setProducerOnly(true);
        component.setLenientProperties(true);

        Set<ComponentOption> componentOptions = orderedSet(new ComponentOption("enabled", "boolean", null, null,
                null, "Whether the component is enabled", false, "common", "common", false, orderedSet()));
        Set<EndpointPath> endpointPaths = orderedSet(
                new EndpointPath("second", "int", "false", "10", "Second path parameter", false,
                        "common", "common", false, orderedSet()),
                new EndpointPath("first", "java.lang.String", "true", null, "First path parameter", false,
                        "common", "common", false, orderedSet()));
        Set<EndpointOption> endpointOptions = orderedSet(
                new EndpointOption("consumerSecret", "java.lang.String", null, null, null,
                        "Consumer-only option", null, null, false, false, "consumer", "consumer", false,
                        orderedSet()),
                new EndpointOption("customHeaders", "java.lang.String", null, null, null,
                        "Headers to include", "header.", "headers", true, false, "producer", "producer,advanced",
                        false, orderedSet()),
                new EndpointOption("mode", "com.example.Mode", "true", "AUTO", null,
                        "Execution mode", null, null, false, false, "common", "common", true,
                        orderedSet("AUTO", "MANUAL")));

        String json = new EndpointAnnotationProcessor().createParameterJsonSchema(
                component, componentOptions, endpointPaths, endpointOptions);
        Map<String, Map<String, String>> componentRows = byName(
                JsonSchemaHelper.parseJsonSchema("componentProperties", json, true));
        Map<String, Map<String, String>> propertyRows = byName(
                JsonSchemaHelper.parseJsonSchema("properties", json, true));

        assertThat(json).contains("\"producerOnly\": \"true\"");
        assertThat(json).contains("\"lenientProperties\": \"true\"");
        assertThat(json).contains("\"alternativeSyntax\": \"demo:alt\"");
        assertThat(json).doesNotContain("consumerSecret");
        assertThat(json.indexOf("\"first\"")).isLessThan(json.indexOf("\"second\""));

        assertThat(componentRows.get("enabled").get("type")).isEqualTo("boolean");
        assertThat(componentRows.get("enabled").get("defaultValue")).isEqualTo("false");
        assertThat(componentRows.get("enabled").get("description")).isEqualTo("Whether the component is enabled");

        assertThat(propertyRows).containsKeys("first", "second", "customHeaders", "mode");
        assertThat(propertyRows.get("first").get("kind")).isEqualTo("path");
        assertThat(propertyRows.get("first").get("required")).isEqualTo("true");
        assertThat(propertyRows.get("second").get("type")).isEqualTo("integer");
        assertThat(propertyRows.get("customHeaders").get("kind")).isEqualTo("parameter");
        assertThat(propertyRows.get("customHeaders").get("optionalPrefix")).isEqualTo("header.");
        assertThat(propertyRows.get("customHeaders").get("prefix")).isEqualTo("headers");
        assertThat(propertyRows.get("customHeaders").get("multiValue")).isEqualTo("true");
        assertThat(propertyRows.get("mode").get("enum")).isEqualTo("AUTO,MANUAL");
        assertThat(propertyRows.get("mode").get("javaType")).isEqualTo("com.example.Mode");
    }

    @Test
    void documentationHelperReturnsNullForUnknownInheritedSchemas() {
        assertThat(DocumentationHelper.findComponentJavaDoc("unknown", "unknown", "field")).isNull();
        assertThat(DocumentationHelper.findEndpointJavaDoc("unknown", "unknown", "field")).isNull();
    }

    private static EndpointOption endpointOption(String name, String group, String label) {
        return new EndpointOption(name, "java.lang.String", null, null, null, "Option documentation", null, null,
                false, false, group, label, false, orderedSet());
    }

    private static Map<String, Map<String, String>> byName(List<Map<String, String>> rows) {
        Map<String, Map<String, String>> answer = new LinkedHashMap<String, Map<String, String>>();
        for (Map<String, String> row : rows) {
            answer.put(row.get("name"), row);
        }
        return answer;
    }

    @SafeVarargs
    private static <T> Set<T> orderedSet(T... values) {
        return new LinkedHashSet<T>(Arrays.asList(values));
    }

    private static final class RecordingCloseable implements Closeable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
