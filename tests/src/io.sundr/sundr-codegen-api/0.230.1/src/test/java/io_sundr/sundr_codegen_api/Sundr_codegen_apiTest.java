/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_api;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.codegen.api.CodeGenerator;
import io.sundr.codegen.api.CodeGeneratorContext;
import io.sundr.codegen.api.FileOutput;
import io.sundr.codegen.api.Filter;
import io.sundr.codegen.api.Identifier;
import io.sundr.codegen.api.Identifiers;
import io.sundr.codegen.api.Output;
import io.sundr.codegen.api.Renderer;
import io.sundr.codegen.api.Renderers;
import io.sundr.codegen.api.SystemOutput;
import io.sundr.codegen.api.TypeDefIdentifier;
import io.sundr.codegen.api.TypeDefRenderer;
import io.sundr.model.TypeDef;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Sundr_codegen_apiTest {

    @Test
    void serviceLoaderDiscoversTypeDefIdentifierAndRenderer() {
        TypeDef typeDef = TypeDef.forName("com.example.Widget");

        assertThat(Identifiers.findIdentifier(TypeDef.class))
                .hasValueSatisfying(identifier -> {
                    assertThat(identifier.getType()).isEqualTo(TypeDef.class);
                    assertThat(identifier.id(typeDef)).isEqualTo("com.example.Widget");
                });
        assertThat(Renderers.findRenderer(TypeDef.class))
                .hasValueSatisfying(renderer -> {
                    assertThat(renderer.getType()).isEqualTo(TypeDef.class);
                    assertThat(renderer.render(typeDef))
                            .contains("package com.example;")
                            .contains("public class Widget");
                });
    }

    @Test
    void typeDefIdentifierAndRendererImplementTheirConvenienceMethods() {
        TypeDef typeDef = TypeDef.forName("com.example.api.Sample");
        TypeDefIdentifier identifier = new TypeDefIdentifier();
        TypeDefRenderer renderer = new TypeDefRenderer();

        assertThat(identifier.getType()).isEqualTo(TypeDef.class);
        assertThat(identifier.getFunction().apply(typeDef)).isEqualTo("com.example.api.Sample");
        assertThat(identifier.id(typeDef)).isEqualTo("com.example.api.Sample");
        assertThat(renderer.getType()).isEqualTo(TypeDef.class);
        assertThat(renderer.getFunction().apply(typeDef))
                .contains("package com.example.api;")
                .contains("public class Sample");
        assertThat(renderer.render(typeDef))
                .contains("package com.example.api;")
                .contains("public class Sample");
    }

    @Test
    void codeGeneratorUsesDiscoveredServicesAndPublishesActiveIdentifierScope() {
        TypeDef first = TypeDef.forName("com.example.First");
        TypeDef second = TypeDef.forName("com.example.Second");
        Map<String, StringWriter> writersById = new LinkedHashMap<>();
        List<String> activeIdentifiersSeenByOutput = new ArrayList<>();

        CodeGenerator<TypeDef> generator = CodeGenerator.newGenerator(TypeDef.class)
                .withOutput(item -> {
                    String activeId = Identifiers.getIdentifier().id(item);
                    activeIdentifiersSeenByOutput.add(activeId);
                    StringWriter writer = new StringWriter();
                    writersById.put(activeId, writer);
                    return writer;
                })
                .skipping(item -> false)
                .build();

        assertThat(generator.generate(first, second)).isTrue();

        assertThat(activeIdentifiersSeenByOutput).containsExactly("com.example.First", "com.example.Second");
        assertThat(writersById).containsOnlyKeys("com.example.First", "com.example.Second");
        assertThat(writersById.get("com.example.First").toString())
                .contains("package com.example;")
                .contains("public class First");
        assertThat(writersById.get("com.example.Second").toString())
                .contains("package com.example;")
                .contains("public class Second");
        assertThat(Identifiers.getIdentifier()).isNull();
    }

    @Test
    void codeGeneratorBuilderSupportsCustomOutputIdentifierRendererSkipAndDuplicateSuppression() {
        List<String> openedForItems = new ArrayList<>();
        List<StringWriter> writers = new ArrayList<>();

        boolean generated = CodeGenerator.newGenerator(String.class)
                .withOutput(item -> {
                    openedForItems.add(item);
                    StringWriter writer = new StringWriter();
                    writers.add(writer);
                    return writer;
                })
                .withIdentifier(item -> item.substring(0, 1))
                .withRenderer(item -> "item=" + item + ", id=" + Identifiers.getIdentifier().id(item))
                .skipping("skip"::equals)
                .generate("alpha", "aardvark", "skip", "beta");

        assertThat(generated).isTrue();
        assertThat(openedForItems).containsExactly("alpha", "beta");
        assertThat(writers).extracting(StringWriter::toString)
                .containsExactly("item=alpha, id=a", "item=beta, id=b");
        assertThat(Identifiers.getIdentifier()).isNull();
    }

    @Test
    void outputImplementationsCreateWritableDestinations(@TempDir Path tempDir) throws IOException {
        File outputFile = tempDir.resolve("generated.txt").toFile();
        FileOutput<String> fileOutput = new FileOutput<>(outputFile);

        try (Writer writer = fileOutput.create("ignored")) {
            writer.write("file payload");
        }

        assertThat(Files.readString(outputFile.toPath(), StandardCharsets.UTF_8)).isEqualTo("file payload");
        assertThat(new SystemOutput<String>().create("console")).isNotNull();
    }

    @Test
    void markerTypesCanBeUsedByApplicationCode() {
        CodeGeneratorContext context = new TestCodeGeneratorContext();

        assertThat(context).isInstanceOf(CodeGeneratorContext.class);
        assertThat(new Filter()).isNotNull();
    }

    @Test
    void functionalInterfacesExposeConvenienceMethods() {
        StringIdentifier identifier = new StringIdentifier();
        StringRenderer renderer = new StringRenderer();
        Output<String> output = () -> item -> new StringWriter();

        Writer writer = output.create("payload");

        assertThat(identifier.getType()).isEqualTo(String.class);
        assertThat(identifier.id("payload")).isEqualTo("id:payload");
        assertThat(renderer.getType()).isEqualTo(String.class);
        assertThat(renderer.render("payload")).isEqualTo("rendered:payload");
        assertThat(writer).isInstanceOf(StringWriter.class);
    }

    @Test
    void withIdentifierTemporarilyExposesScopeAndClearsItAfterCompletion() {
        StringIdentifier identifier = new StringIdentifier();

        assertThat(Identifiers.getIdentifier()).isNull();
        String result = Identifiers.withIdentifier(identifier).apply(activeIdentifier -> {
            assertThat(activeIdentifier).isSameAs(identifier);
            assertThat(Identifiers.getIdentifier()).isSameAs(identifier);
            return activeIdentifier.id("sample");
        });

        assertThat(result).isEqualTo("id:sample");
        assertThat(Identifiers.getIdentifier()).isNull();
    }

    @Test
    void functionBasedIdentifierScopeSupportsCallableExecution() {
        assertThat(Identifiers.getIdentifier()).isNull();

        String result = Identifiers.withIdentifier((String item) -> "function:" + item).call(() -> {
            assertThat(Identifiers.getIdentifier().id("payload")).isEqualTo("function:payload");
            return Identifiers.getIdentifier().id("callable");
        });

        assertThat(result).isEqualTo("function:callable");
        assertThat(Identifiers.getIdentifier()).isNull();
    }

    private static final class TestCodeGeneratorContext implements CodeGeneratorContext {
    }

    private static final class StringIdentifier implements Identifier<String> {

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public Function<String, String> getFunction() {
            return item -> "id:" + item;
        }
    }

    private static final class StringRenderer implements Renderer<String> {

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public Function<String, String> getFunction() {
            return item -> "rendered:" + item;
        }
    }
}
