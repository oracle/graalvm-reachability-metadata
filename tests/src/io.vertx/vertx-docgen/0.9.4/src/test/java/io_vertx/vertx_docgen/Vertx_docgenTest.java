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
    void generatesAsciidocFromAnnotatedPackageDocumentation(@TempDir Path temporaryDirectory) throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path outputDirectory = temporaryDirectory.resolve("documentation");
        Path classDirectory = temporaryDirectory.resolve("classes");
        Path packageInfo = sourceDirectory.resolve("example/docs/package-info.java");
        Path greeting = sourceDirectory.resolve("example/docs/Greeting.java");
        Files.createDirectories(packageInfo.getParent());
        Files.createDirectories(outputDirectory);
        Files.createDirectories(classDirectory);
        Files.writeString(packageInfo, """
                /**
                 * = Greeting guide
                 *
                 * This guide documents {@link Greeting} and uses {@code greeting} as an example.
                 */
                @io.vertx.docgen.Document(fileName = "greeting-guide.adoc")
                package example.docs;
                """, StandardCharsets.UTF_8);
        Files.writeString(greeting, """
                package example.docs;

                /** A documented greeting type. */
                public class Greeting {
                    /** Returns a greeting for the supplied name. */
                    public String message(String name) {
                        return "Hello " + name;
                    }
                }
                """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("system Java compiler").isNotNull();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDirectory));
            List<String> options = List.of(
                    "-proc:only",
                    "-Adocgen.output=" + outputDirectory);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    fileManager.getJavaFileObjectsFromPaths(List.of(packageInfo, greeting)));
            task.setProcessors(List.of(new DocGenProcessor(new JavaDocGenerator())));

            assertThat(task.call()).as(diagnosticText(diagnostics)).isTrue();
        }

        Path generatedGuide = outputDirectory.resolve("greeting-guide.adoc");
        assertThat(generatedGuide).exists();
        assertThat(Files.readString(generatedGuide, StandardCharsets.UTF_8))
                .contains("Greeting guide")
                .contains("Greeting")
                .contains("greeting");
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
