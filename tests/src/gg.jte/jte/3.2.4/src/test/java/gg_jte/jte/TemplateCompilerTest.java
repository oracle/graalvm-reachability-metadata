/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package gg_jte.jte;

import static org.assertj.core.api.Assertions.assertThat;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.extension.api.JteConfig;
import gg.jte.extension.api.JteExtension;
import gg.jte.extension.api.ParamDescription;
import gg.jte.extension.api.TemplateDescription;
import gg.jte.resolve.DirectoryCodeResolver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TemplateCompilerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsConfiguredExtensionWhileGeneratingTemplates() throws IOException {
        Path templateRoot = temporaryDirectory.resolve("templates");
        Path generatedSourcesRoot = temporaryDirectory.resolve("generated-sources");
        Path generatedResourcesRoot = temporaryDirectory.resolve("generated-resources");
        Files.createDirectories(templateRoot);
        Files.writeString(templateRoot.resolve("welcome.jte"), """
                @param String visitor
                <p>Hello, ${visitor}!</p>
                """);

        TemplateEngine templateEngine = TemplateEngine.create(
                new DirectoryCodeResolver(templateRoot),
                generatedSourcesRoot,
                ContentType.Html,
                TemplateCompilerTest.class.getClassLoader(),
                "example.templates");
        templateEngine.setTargetResourceDirectory(generatedResourcesRoot);
        templateEngine.setProjectNamespace("example.application");
        templateEngine.setExtensions(Map.of(
                TemplateReportExtension.class.getName(),
                Map.of("reportName", "template-report.txt")));

        List<String> generatedTemplates = templateEngine.generateAll();

        assertThat(generatedTemplates).singleElement().satisfies(generatedTemplate ->
                assertThat(generatedSourcesRoot.resolve(generatedTemplate)).isRegularFile());
        assertThat(generatedResourcesRoot.resolve("template-report.txt")).hasContent("""
                project=example.application
                contentType=Html
                template=welcome.jte
                parameters=String visitor
                """);
    }

    public static final class TemplateReportExtension implements JteExtension {
        private String reportName;

        public TemplateReportExtension() {
        }

        @Override
        public String name() {
            return "template report";
        }

        @Override
        public JteExtension init(Map<String, String> settings) {
            reportName = settings.get("reportName");
            return this;
        }

        @Override
        public Collection<Path> generate(JteConfig config, Set<TemplateDescription> templates) {
            TemplateDescription template = templates.iterator().next();
            String parameters = template.params().stream()
                    .map(TemplateReportExtension::formatParameter)
                    .collect(Collectors.joining(", "));
            String report = """
                    project=%s
                    contentType=%s
                    template=%s
                    parameters=%s
                    """.formatted(config.projectNamespace(), config.contentType(), template.name(), parameters);
            Path reportPath = config.generatedResourcesRoot().resolve(reportName);
            try {
                Files.createDirectories(reportPath.getParent());
                Files.writeString(reportPath, report);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return List.of(reportPath);
        }

        private static String formatParameter(ParamDescription parameter) {
            return parameter.type() + " " + parameter.name();
        }
    }
}
