/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_docgen;

import io.vertx.docgen.DocGenProcessor;
import io.vertx.docgen.JavaDocGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class Vertx_docgenTest {
    @Test
    void generatesAsciidocWithJavaLinksAndLiteralJavadoc(@TempDir Path temporaryDirectory) throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path outputDirectory = temporaryDirectory.resolve("documentation");
        Path packageInfo = sourceDirectory.resolve("example/docs/package-info.java");
        Path greeting = sourceDirectory.resolve("example/docs/Greeting.java");
        Files.createDirectories(packageInfo.getParent());
        Files.writeString(packageInfo, """
                /**
                 * = Greeting guide
                 *
                 * This guide documents {@link example.docs.Greeting},
                 * {@link example.docs.Greeting#message(String) a personalized greeting}, and
                 * {@link example.docs.Greeting#DEFAULT_GREETING}. It uses {@code greeting} as an example.
                 */
                @io.vertx.docgen.Document(fileName = "guides/greeting-guide.adoc")
                package example.docs;
                """, StandardCharsets.UTF_8);
        Files.writeString(greeting, """
                package example.docs;

                /** A documented greeting type. */
                public class Greeting {
                    public static final String DEFAULT_GREETING = "Hello";

                    /** Returns a greeting for the supplied name. */
                    public String message(String name) {
                        return DEFAULT_GREETING + " " + name;
                    }
                }
                """, StandardCharsets.UTF_8);

        compileDocumentation(sourceDirectory, outputDirectory, List.of(packageInfo, greeting));

        Path generatedGuide = outputDirectory.resolve("guides/greeting-guide.adoc");
        assertThat(generatedGuide).exists();
        assertThat(Files.readString(generatedGuide, StandardCharsets.UTF_8))
                .contains("= Greeting guide")
                .contains("`link:../../apidocs/example/docs/Greeting.html[Greeting]`")
                .contains("`link:../../apidocs/example/docs/Greeting.html#message-java.lang.String-[a personalized greeting]`")
                .contains("`link:../../apidocs/example/docs/Greeting.html#DEFAULT_GREETING[Greeting.DEFAULT_GREETING]`")
                .contains("`greeting`");
    }

    @Test
    void includesAnnotatedExampleMethodSource(@TempDir Path temporaryDirectory) throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path outputDirectory = temporaryDirectory.resolve("documentation");
        Path packageInfo = sourceDirectory.resolve("example/source/package-info.java");
        Path example = sourceDirectory.resolve("example/source/GreetingExample.java");
        Files.createDirectories(packageInfo.getParent());
        Files.writeString(packageInfo, """
                /**
                 * = Source guide
                 *
                 * {@link example.source.GreetingExample#greeting(String)}
                 */
                @io.vertx.docgen.Document(fileName = "guides/source-guide.adoc")
                package example.source;
                """, StandardCharsets.UTF_8);
        Files.writeString(example, """
                package example.source;

                public class GreetingExample {
                    @io.vertx.docgen.Source
                    public static String greeting(String name) {
                        return "Hello " + name;
                    }
                }
                """, StandardCharsets.UTF_8);

        compileDocumentation(sourceDirectory, outputDirectory, List.of(packageInfo, example));

        Path generatedGuide = outputDirectory.resolve("guides/source-guide.adoc");
        assertThat(generatedGuide).exists();
        assertThat(Files.readString(generatedGuide, StandardCharsets.UTF_8))
                .contains("return \"Hello \" + name;")
                .doesNotContain("GreetingExample.html#greeting-java.lang.String-");
    }

    @Test
    void usesTheQualifiedPackageNameWhenDocumentFileNameIsNotConfigured(@TempDir Path temporaryDirectory)
            throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path outputDirectory = temporaryDirectory.resolve("documentation");
        Path packageInfo = sourceDirectory.resolve("example/defaultdoc/package-info.java");
        Files.createDirectories(packageInfo.getParent());
        Files.writeString(packageInfo, """
                /**
                 * = Default document
                 *
                 * This document relies on the default file name.
                 */
                @io.vertx.docgen.Document
                package example.defaultdoc;
                """, StandardCharsets.UTF_8);

        compileDocumentation(sourceDirectory, outputDirectory, List.of(packageInfo));

        Path generatedGuide = outputDirectory.resolve("example.defaultdoc.adoc");
        assertThat(generatedGuide).exists();
        assertThat(Files.readString(generatedGuide, StandardCharsets.UTF_8))
                .contains("= Default document")
                .contains("This document relies on the default file name.");
    }

    @Test
    void generatesDocumentationFromConfiguredSourceFiles(@TempDir Path temporaryDirectory) throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path outputDirectory = temporaryDirectory.resolve("documentation");
        Path documentDirectory = temporaryDirectory.resolve("manual");
        Path document = documentDirectory.resolve("getting-started.adoc");
        Path handbook = sourceDirectory.resolve("example/manual/Handbook.java");
        Files.createDirectories(documentDirectory);
        Files.createDirectories(handbook.getParent());
        Files.writeString(document, """
                = Getting started

                Consult {@link example.manual.Handbook} before continuing.
                """, StandardCharsets.UTF_8);
        Files.writeString(handbook, """
                package example.manual;

                public class Handbook {
                }
                """, StandardCharsets.UTF_8);

        compileDocumentation(
                sourceDirectory,
                outputDirectory,
                List.of(handbook),
                List.of("-Adocgen.source=" + documentDirectory));

        Path generatedDocument = outputDirectory.resolve("getting-started.adoc");
        assertThat(generatedDocument).exists();
        assertThat(Files.readString(generatedDocument, StandardCharsets.UTF_8))
                .contains("= Getting started")
                .contains("`link:../../apidocs/example/manual/Handbook.html[Handbook]`");
    }

    private static void compileDocumentation(
            Path sourceDirectory, Path outputDirectory, List<Path> sourceFiles) throws IOException {
        compileDocumentation(sourceDirectory, outputDirectory, sourceFiles, List.of());
    }

    private static void compileDocumentation(
            Path sourceDirectory, Path outputDirectory, List<Path> sourceFiles, List<String> processorOptions)
            throws IOException {
        Path classDirectory = sourceDirectory.resolveSibling("classes");
        Files.createDirectories(outputDirectory);
        Files.createDirectories(classDirectory);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("system Java compiler").isNotNull();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDirectory));
            List<String> options = new ArrayList<>(List.of("-proc:only", "-Adocgen.output=" + outputDirectory));
            options.addAll(processorOptions);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    fileManager.getJavaFileObjectsFromPaths(sourceFiles));
            task.setProcessors(List.of(new DocGenProcessor(new JavaDocGenerator())));

            assertThat(task.call()).as(diagnosticText(diagnostics.getDiagnostics())).isTrue();
        }
    }

    private static String diagnosticText(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        StringBuilder text = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            text.append(diagnostic.getKind())
                    .append(": ")
                    .append(diagnostic.getMessage(Locale.ROOT))
                    .append(System.lineSeparator());
        }
        return text.toString();
    }
}
