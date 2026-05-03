/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.apt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;

import org.apache.camel.tools.apt.DocumentationHelper;
import org.apache.camel.tools.apt.EipAnnotationProcessor;
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
    void serviceLoaderDiscoversBothAnnotationProcessors() {
        List<Processor> processors = new ArrayList<>();
        for (Processor processor : ServiceLoader.load(Processor.class)) {
            if (processor instanceof EndpointAnnotationProcessor || processor instanceof EipAnnotationProcessor) {
                processors.add(processor);
            }
        }

        assertThat(processors)
                .extracting(processor -> processor.getClass().getName())
                .containsExactlyInAnyOrder(
                        EndpointAnnotationProcessor.class.getName(),
                        EipAnnotationProcessor.class.getName());
        assertThat(processors)
                .allSatisfy(processor -> assertThat(processor.getSupportedSourceVersion())
                        .isEqualTo(SourceVersion.RELEASE_7));
        assertThat(processors)
                .filteredOn(EndpointAnnotationProcessor.class::isInstance)
                .singleElement()
                .satisfies(processor -> assertThat(processor.getSupportedAnnotationTypes())
                        .containsExactly("org.apache.camel.spi.*"));
        assertThat(processors)
                .filteredOn(EipAnnotationProcessor.class::isInstance)
                .singleElement()
                .satisfies(processor -> assertThat(processor.getSupportedAnnotationTypes())
                        .containsExactlyInAnyOrder("javax.xml.bind.annotation.*", "org.apache.camel.spi.Label"));
    }

    @Test
    void endpointProcessorBuildsMetadataSchemaFromPublicModelObjects() {
        ComponentModel component = componentModel();
        Set<ComponentOption> componentOptions = new LinkedHashSet<>();
        componentOptions.add(new ComponentOption(
                "enabled",
                "boolean",
                "true",
                "",
                " component startup flag",
                "Whether the component is enabled.\n@param ignored",
                false,
                "common",
                "common",
                false,
                Collections.emptySet()));

        Set<EndpointPath> endpointPaths = new LinkedHashSet<>();
        endpointPaths.add(new EndpointPath(
                "port",
                "int",
                "false",
                "8080",
                "Port number for the endpoint.",
                false,
                "path",
                "producer",
                false,
                Collections.emptySet()));
        endpointPaths.add(new EndpointPath(
                "host",
                "java.lang.String",
                "true",
                "localhost",
                "Main <b>host</b> for {@link java.net.URI}.",
                false,
                "path",
                "producer",
                false,
                Collections.emptySet()));
        endpointPaths.add(new EndpointPath(
                "pollDelay",
                "long",
                "false",
                "1000",
                "Consumer-only path must be filtered for producer-only components.",
                false,
                "consumer",
                "consumer",
                false,
                Collections.emptySet()));

        Set<EndpointOption> endpointOptions = new LinkedHashSet<>();
        endpointOptions.add(new EndpointOption(
                "mode",
                "com.example.Mode",
                "false",
                "fast",
                "",
                "Operation mode for the endpoint.",
                "",
                "",
                false,
                false,
                "producer",
                "producer",
                true,
                linkedSet("fast", "safe")));
        endpointOptions.add(new EndpointOption(
                "headers",
                "java.util.List<java.lang.String>",
                "false",
                "",
                "",
                "Header names to include.",
                "header.",
                "headers.",
                true,
                false,
                "common",
                "common",
                false,
                Collections.emptySet()));
        endpointOptions.add(new EndpointOption(
                "readLock",
                "boolean",
                "false",
                "",
                "",
                "Consumer-only option must be filtered for producer-only components.",
                "",
                "",
                false,
                false,
                "consumer",
                "consumer",
                false,
                Collections.emptySet()));

        String schema = new EndpointAnnotationProcessor().createParameterJsonSchema(
                component, componentOptions, endpointPaths, endpointOptions);

        assertThat(schema)
                .contains("\"component\": {")
                .contains("\"scheme\": \"sample\"")
                .contains("\"extendsScheme\": \"base\"")
                .contains("\"alternativeSyntax\": \"sample:host?port=port\"")
                .contains("\"producerOnly\": \"true\"")
                .contains("\"lenientProperties\": \"true\"")
                .contains("\"groupId\": \"org.example\"")
                .contains("\"artifactId\": \"sample-camel-component\"");

        Map<String, Map<String, String>> componentProperties = byName(
                JsonSchemaHelper.parseJsonSchema("componentProperties", schema, true));
        assertThat(componentProperties).containsOnlyKeys("enabled");
        assertThat(componentProperties.get("enabled"))
                .containsEntry("kind", "property")
                .containsEntry("required", "true")
                .containsEntry("type", "boolean")
                .containsEntry("defaultValue", "false")
                .containsEntry("description", "Whether the component is enabled.");

        Map<String, Map<String, String>> properties = byName(
                JsonSchemaHelper.parseJsonSchema("properties", schema, true));
        assertThat(properties).containsOnlyKeys("host", "port", "headers", "mode");
        assertThat(new ArrayList<>(properties.keySet())).containsSubsequence("host", "port");
        assertThat(properties.get("host"))
                .containsEntry("kind", "path")
                .containsEntry("required", "true")
                .containsEntry("type", "string")
                .containsEntry("defaultValue", "localhost")
                .containsEntry("description", "Main host for java.net.URI.");
        assertThat(properties.get("port"))
                .containsEntry("kind", "path")
                .containsEntry("type", "integer")
                .containsEntry("defaultValue", "8080");
        assertThat(properties.get("headers"))
                .containsEntry("kind", "parameter")
                .containsEntry("type", "array")
                .containsEntry("javaType", "java.util.List<java.lang.String>")
                .containsEntry("optionalPrefix", "header.")
                .containsEntry("prefix", "headers.")
                .containsEntry("multiValue", "true");
        assertThat(properties.get("mode"))
                .containsEntry("kind", "parameter")
                .containsEntry("type", "string")
                .containsEntry("javaType", "com.example.Mode")
                .containsEntry("enum", "fast,safe")
                .containsEntry("defaultValue", "fast");
    }

    @Test
    void jsonSchemaHelperMapsTypesEscapesValuesAndParsesSections() {
        assertThat(JsonSchemaHelper.getType(null, false)).isEqualTo("object");
        assertThat(JsonSchemaHelper.getType("java.net.URI", false)).isEqualTo("string");
        assertThat(JsonSchemaHelper.getType("java.io.File", false)).isEqualTo("string");
        assertThat(JsonSchemaHelper.getType("java.util.Collection<java.lang.String>", false)).isEqualTo("array");
        assertThat(JsonSchemaHelper.getType("com.example.Mode", true)).isEqualTo("enum");
        assertThat(JsonSchemaHelper.getPrimitiveType("byte[]")).isEqualTo("string");
        assertThat(JsonSchemaHelper.getPrimitiveType("java.lang.Byte[]")).isEqualTo("array");
        assertThat(JsonSchemaHelper.getPrimitiveType("char")).isEqualTo("string");
        assertThat(JsonSchemaHelper.getPrimitiveType("long")).isEqualTo("integer");
        assertThat(JsonSchemaHelper.getPrimitiveType("double")).isEqualTo("number");

        String description = JsonSchemaHelper.sanitizeDescription("""
                First line with <em>markup</em> and {@link java.lang.String}.
                Second line; unsupported symbols [] are removed.
                @return ignored
                """, true);
        assertThat(description).isEqualTo("First line with markup and java.lang.String.");

        String enumJson = JsonSchemaHelper.toJson(
                "kind",
                "parameter",
                Boolean.TRUE,
                "com.example.Kind",
                "\"",
                "Choose a kind.",
                Boolean.FALSE,
                "common",
                "common",
                true,
                linkedSet("fast", "safe"),
                false,
                Collections.emptySet(),
                "",
                "",
                false);
        String oneOfJson = JsonSchemaHelper.toJson(
                "target",
                "parameter",
                Boolean.FALSE,
                "java.lang.Object",
                "\\",
                "Target endpoint.",
                Boolean.FALSE,
                "advanced",
                "advanced",
                false,
                Collections.emptySet(),
                true,
                linkedSet("direct", "seda"),
                "opt.",
                "prefix.",
                true);
        String document = """
                {
                  "properties": {
                %s,
                %s
                  }
                }
                """.formatted(enumJson, oneOfJson);

        Map<String, Map<String, String>> properties = byName(
                JsonSchemaHelper.parseJsonSchema("properties", document, true));
        assertThat(properties.get("kind"))
                .containsEntry("kind", "parameter")
                .containsEntry("required", "true")
                .containsEntry("type", "string")
                .containsEntry("javaType", "com.example.Kind")
                .containsEntry("enum", "fast,safe")
                .containsEntry("defaultValue", "\"");
        assertThat(properties.get("target"))
                .containsEntry("type", "object")
                .containsEntry("javaType", "java.lang.Object")
                .containsEntry("oneOf", "direct,seda")
                .containsEntry("defaultValue", "\\")
                .containsEntry("optionalPrefix", "opt.")
                .containsEntry("prefix", "prefix.")
                .containsEntry("multiValue", "true");
    }

    @Test
    void documentationHelperFindsDescriptionsInGeneratedComponentJson() throws IOException {
        Path schemaFile = Path.of(
                "..", "camel-jms", "target", "classes", "org", "apache", "camel", "component", "jms", "jms.json");
        String originalContent = Files.exists(schemaFile) ? Files.readString(schemaFile, StandardCharsets.UTF_8) : null;

        try {
            Files.createDirectories(schemaFile.getParent());
            Files.writeString(schemaFile, """
                    {
                      "componentProperties": {
                        "connectionFactory": { "kind": "property", "description": "Creates JMS connections." },
                        "cacheLevel": { "kind": "property", "description": "Caches JMS resources." }
                      },
                      "properties": {
                        "destinationName": { "kind": "path", "description": "Name of the JMS destination." },
                        "replyTo": { "kind": "parameter", "description": "Reply destination for messages." }
                      }
                    }
                    """, StandardCharsets.UTF_8);

            assertThat(DocumentationHelper.findComponentJavaDoc("camel-jms", "jms", "connectionFactory"))
                    .isEqualTo("Creates JMS connections.");
            assertThat(DocumentationHelper.findEndpointJavaDoc("camel-jms", "jms", "destinationName"))
                    .isEqualTo("Name of the JMS destination.");
            assertThat(DocumentationHelper.findEndpointJavaDoc("camel-jms", "jms", "unknownOption")).isNull();
        } finally {
            if (originalContent == null) {
                Files.deleteIfExists(schemaFile);
            } else {
                Files.writeString(schemaFile, originalContent, StandardCharsets.UTF_8);
            }
        }
    }

    @Test
    void endpointHelperComparatorsOrderEndpointMetadataForDocumentation() {
        List<EndpointPath> paths = new ArrayList<>();
        paths.add(endpointPath("prefix"));
        paths.add(endpointPath("name"));
        paths.add(endpointPath("bucket"));

        paths.sort(EndpointHelper.createPathComparator("aws-s3://bucket/name?prefix=prefix"));

        assertThat(paths)
                .extracting(EndpointPath::getName)
                .containsExactly("bucket", "name", "prefix");

        List<EndpointOption> options = new ArrayList<>();
        options.add(endpointOption("producerPoolSize", "producer", "producer"));
        options.add(endpointOption("consumerDelay", "consumer", "consumer"));
        options.add(endpointOption("bridgeErrorHandler", "consumer (advanced)", "consumer,advanced"));
        options.add(endpointOption("zookeeperPath", "custom", "custom"));
        options.add(endpointOption("timeout", "common", "common"));
        options.add(endpointOption("autoStartup", "common", "common"));
        options.add(endpointOption("lazyStartProducer", "common (advanced)", "common,advanced"));

        options.sort(EndpointHelper.createGroupAndLabelComparator());

        assertThat(options)
                .extracting(EndpointOption::getName)
                .containsExactly(
                        "autoStartup",
                        "timeout",
                        "lazyStartProducer",
                        "consumerDelay",
                        "bridgeErrorHandler",
                        "producerPoolSize",
                        "zookeeperPath");
    }

    @Test
    void helperUtilitiesUseCamelAptFormattingRules() throws IOException {
        assertThat(Strings.isNullOrEmpty(null)).isTrue();
        assertThat(Strings.isNullOrEmpty("")).isTrue();
        assertThat(Strings.isNullOrEmpty("null")).isTrue();
        assertThat(Strings.safeNull("value")).isEqualTo("value");
        assertThat(Strings.getOrElse(null, "fallback")).isEqualTo("fallback");
        assertThat(Strings.after("component:option", ":")).isEqualTo("option");
        assertThat(Strings.after("component", ":")).isNull();
        assertThat(Strings.canonicalClassName("java.util.List<java.lang.String>")).isEqualTo("java.util.List");
        assertThat(Strings.doubleQuote("camel")).isEqualTo("\"camel\"");
        assertThat(Strings.singleQuote("camel")).isEqualTo("'camel'");

        CollectionStringBuffer buffer = new CollectionStringBuffer(" | ");
        buffer.append("first");
        buffer.append("second");
        buffer.setSeparator(" -> ");
        buffer.append("third");
        assertThat(buffer.getSeparator()).isEqualTo(" -> ");
        assertThat(buffer).hasToString("first | second -> third");

        assertThat(EndpointHelper.labelAsGroupName(null, false, false)).isEqualTo("common");
        assertThat(EndpointHelper.labelAsGroupName("consumer,advanced", false, false)).isEqualTo("consumer (advanced)");
        assertThat(EndpointHelper.labelAsGroupName("producer", false, false)).isEqualTo("producer");
        assertThat(EndpointHelper.labelAsGroupName("", true, false)).isEqualTo("consumer");
        assertThat(EndpointHelper.labelAsGroupName("", false, true)).isEqualTo("producer");

        byte[] bytes = "# comment\n\nalpha\n  # indented comment\nbeta\n".getBytes(StandardCharsets.UTF_8);
        assertThat(IOHelper.loadText(new ByteArrayInputStream(bytes), true)).isEqualTo("alpha\nbeta\n");
        assertThat(IOHelper.loadText(new ByteArrayInputStream(bytes), false))
                .isEqualTo("# comment\n\nalpha\n  # indented comment\nbeta\n");
    }

    @Test
    void modelObjectsExposeEndpointMetadataAndNameBasedEquality() {
        EndpointPath path = new EndpointPath(
                "directory",
                "java.lang.String",
                "true",
                "/tmp",
                "Input directory.",
                true,
                "common",
                "file",
                true,
                linkedSet("inbox", "outbox"));
        EndpointOption option = new EndpointOption(
                "delay",
                "long",
                "false",
                "1000",
                " milliseconds",
                "Delay between polls",
                "consumer.",
                "scheduler.",
                true,
                false,
                "consumer",
                "consumer",
                false,
                Collections.emptySet());
        ComponentOption componentOption = new ComponentOption(
                "bridgeErrorHandler",
                "boolean",
                "false",
                "false",
                " routes exceptions to Camel error handler",
                "Bridge consumer errors",
                false,
                "consumer",
                "consumer",
                false,
                Collections.emptySet());

        assertThat(path.getName()).isEqualTo("directory");
        assertThat(path.getType()).isEqualTo("java.lang.String");
        assertThat(path.getRequired()).isEqualTo("true");
        assertThat(path.getDefaultValue()).isEqualTo("/tmp");
        assertThat(path.isDeprecated()).isTrue();
        assertThat(path.getGroup()).isEqualTo("common");
        assertThat(path.getLabel()).isEqualTo("file");
        assertThat(path.isEnumType()).isTrue();
        assertThat(path.getEnumValuesAsHtml()).isEqualTo("inbox<br/>outbox");
        assertThat(path).isEqualTo(new EndpointPath(
                "directory", "int", "false", "", "", false, "advanced", "advanced", false, Collections.emptySet()));
        assertThat(path).hasSameHashCodeAs(new EndpointPath(
                "directory", "int", "false", "", "", false, "advanced", "advanced", false, Collections.emptySet()));

        assertThat(option.getOptionalPrefix()).isEqualTo("consumer.");
        assertThat(option.getPrefix()).isEqualTo("scheduler.");
        assertThat(option.isMultiValue()).isTrue();
        assertThat(option.getDocumentationWithNotes())
                .isEqualTo("Delay between polls. Default value notice:  milliseconds");
        assertThat(option).isEqualTo(new EndpointOption(
                "delay", "int", "false", "", "", "", "", "", false, false, "common", "common", false,
                Collections.emptySet()));

        assertThat(componentOption.getName()).isEqualTo("bridgeErrorHandler");
        assertThat(componentOption.getDocumentationWithNotes())
                .isEqualTo("Bridge consumer errors. Default value notice:  routes exceptions to Camel error handler");
        assertThat(componentOption).isEqualTo(new ComponentOption(
                "bridgeErrorHandler", "boolean", "false", "", "", "", false, "common", "common", false,
                Collections.emptySet()));
    }

    private static EndpointPath endpointPath(String name) {
        return new EndpointPath(
                name,
                "java.lang.String",
                "false",
                "",
                "Path documentation.",
                false,
                "path",
                "common",
                false,
                Collections.emptySet());
    }

    private static EndpointOption endpointOption(String name, String group, String label) {
        return new EndpointOption(
                name,
                "java.lang.String",
                "false",
                "",
                "",
                "Option documentation.",
                "",
                "",
                false,
                false,
                group,
                label,
                false,
                Collections.emptySet());
    }

    private static ComponentModel componentModel() {
        ComponentModel component = new ComponentModel("sample");
        component.setExtendsScheme("base");
        component.setSyntax("sample:host:port");
        component.setAlternativeSyntax("sample:host?port=port");
        component.setJavaType("org.example.SampleComponent");
        component.setTitle("Sample");
        component.setDescription("Sample component");
        component.setGroupId("org.example");
        component.setArtifactId("sample-camel-component");
        component.setVersionId("test-version");
        component.setLabel("testing");
        component.setProducerOnly(true);
        component.setLenientProperties(true);
        return component;
    }

    private static Set<String> linkedSet(String... values) {
        Set<String> answer = new LinkedHashSet<>();
        Collections.addAll(answer, values);
        return answer;
    }

    private static Map<String, Map<String, String>> byName(List<Map<String, String>> entries) {
        Map<String, Map<String, String>> answer = new LinkedHashMap<>();
        for (Map<String, String> entry : entries) {
            answer.put(entry.get("name"), entry);
        }
        return answer;
    }
}
