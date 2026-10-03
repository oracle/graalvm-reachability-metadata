/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package gg_jte.jte_extension_api;

import static org.assertj.core.api.Assertions.assertThat;

import gg.jte.ContentType;
import gg.jte.extension.api.JteConfig;
import gg.jte.extension.api.JteExtension;
import gg.jte.extension.api.ParamDescription;
import gg.jte.extension.api.TemplateDescription;
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

public class Jte_extension_apiTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void extensionProducesAReportFromTemplateAndConfiguration() {
        Path generatedSourcesRoot = temporaryDirectory.resolve("generated-sources");
        Path generatedResourcesRoot = temporaryDirectory.resolve("generated-resources");
        ClassLoader classLoader = Jte_extension_apiTest.class.getClassLoader();
        JteConfig config = new TestJteConfig(
                generatedSourcesRoot,
                generatedResourcesRoot,
                "example.application",
                "example.templates",
                ContentType.Html,
                classLoader);
        TemplateDescription template = new TestTemplateDescription(
                "welcome.jte",
                "example.templates",
                "WelcomeGenerated",
                List.of(new TestParamDescription("String", "visitor", "\"guest\"")),
                List.of("java.util.Locale"));
        JteExtension extension = new ReportingExtension();

        assertThat(extension.name()).isEqualTo("reporting extension");
        assertThat(extension.init(Map.of("mode", "strict"))).isSameAs(extension);
        assertThat(config.generatedSourcesRoot()).isEqualTo(generatedSourcesRoot);
        assertThat(config.classLoader()).isSameAs(classLoader);

        Collection<Path> generatedFiles = extension.generate(config, Set.of(template));

        Path report = generatedResourcesRoot.resolve("reports/welcome.jte.txt");
        assertThat(generatedFiles).containsExactly(report);
        assertThat(report).hasContent("""
                project=example.application
                contentType=Html
                package=example.templates
                sourceRoot=%s
                template=welcome.jte
                qualifiedName=example.templates.WelcomeGenerated
                parameters=String visitor=\"guest\"
                imports=java.util.Locale
                """.formatted(generatedSourcesRoot));
    }

    @Test
    void extensionGeneratesAReportForEveryTemplateInTheSet() {
        Path generatedSourcesRoot = temporaryDirectory.resolve("generated-sources");
        Path generatedResourcesRoot = temporaryDirectory.resolve("generated-resources");
        JteConfig config = new TestJteConfig(
                generatedSourcesRoot,
                generatedResourcesRoot,
                "example.application",
                "example.templates",
                ContentType.Html,
                Jte_extension_apiTest.class.getClassLoader());
        Set<TemplateDescription> templates = Set.of(
                new TestTemplateDescription(
                        "welcome.jte", "example.templates", "WelcomeGenerated", List.of(), List.of()),
                new TestTemplateDescription(
                        "invoice.jte", "example.billing", "InvoiceGenerated", List.of(), List.of()));

        Collection<Path> generatedFiles = new BatchReportingExtension().generate(config, templates);

        Path report = generatedResourcesRoot.resolve("reports/templates.txt");
        assertThat(generatedFiles).containsExactly(report);
        assertThat(report).hasContent("""
                invoice.jte
                welcome.jte
                """);
    }

    @Test
    void extensionCanReturnAConfiguredReplacementFromInitialization() {
        Path generatedResourcesRoot = temporaryDirectory.resolve("configured-resources");
        JteConfig config = new TestJteConfig(
                temporaryDirectory.resolve("generated-sources"),
                generatedResourcesRoot,
                "example.application",
                "example.templates",
                ContentType.Html,
                Jte_extension_apiTest.class.getClassLoader());
        JteExtension extension = new ConfigurableReportingExtension();

        JteExtension configuredExtension = extension.init(Map.of("reportName", "configured"));

        assertThat(configuredExtension).isNotSameAs(extension);
        Path report = generatedResourcesRoot.resolve("reports/configured.txt");
        assertThat(configuredExtension.generate(config, Set.of())).containsExactly(report);
        assertThat(report).hasContent("reportName=configured");
    }

    @Test
    void templateDescriptionBuildsItsFullyQualifiedClassName() {
        TemplateDescription template = new TestTemplateDescription(
                "invoice.jte",
                "example.billing",
                "InvoiceGenerated",
                List.of(),
                List.of("java.math.BigDecimal"));

        assertThat(template.name()).isEqualTo("invoice.jte");
        assertThat(template.packageName()).isEqualTo("example.billing");
        assertThat(template.className()).isEqualTo("InvoiceGenerated");
        assertThat(template.fullyQualifiedClassName()).isEqualTo("example.billing.InvoiceGenerated");
        assertThat(template.params()).isEmpty();
        assertThat(template.imports()).containsExactly("java.math.BigDecimal");
    }

    private static final class BatchReportingExtension implements JteExtension {
        @Override
        public String name() {
            return "batch reporting extension";
        }

        @Override
        public Collection<Path> generate(JteConfig config, Set<TemplateDescription> templates) {
            String report = templates.stream()
                    .map(TemplateDescription::name)
                    .sorted()
                    .collect(Collectors.joining("\n", "", "\n"));
            Path reportPath = config.generatedResourcesRoot().resolve("reports/templates.txt");
            try {
                Files.createDirectories(reportPath.getParent());
                Files.writeString(reportPath, report);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return List.of(reportPath);
        }
    }

    private static final class ConfigurableReportingExtension implements JteExtension {
        private final String reportName;

        private ConfigurableReportingExtension() {
            this("default");
        }

        private ConfigurableReportingExtension(String reportName) {
            this.reportName = reportName;
        }

        @Override
        public String name() {
            return "configurable reporting extension";
        }

        @Override
        public Collection<Path> generate(JteConfig config, Set<TemplateDescription> templates) {
            Path reportPath = config.generatedResourcesRoot().resolve("reports/" + reportName + ".txt");
            try {
                Files.createDirectories(reportPath.getParent());
                Files.writeString(reportPath, "reportName=" + reportName);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return List.of(reportPath);
        }

        @Override
        public JteExtension init(Map<String, String> values) {
            return new ConfigurableReportingExtension(values.get("reportName"));
        }
    }

    private static final class ReportingExtension implements JteExtension {
        @Override
        public String name() {
            return "reporting extension";
        }

        @Override
        public Collection<Path> generate(JteConfig config, Set<TemplateDescription> templates) {
            TemplateDescription template = templates.iterator().next();
            String parameters = template.params().stream()
                    .map(parameter -> parameter.type() + " " + parameter.name() + "=" + parameter.defaultValue())
                    .reduce((first, second) -> first + ", " + second)
                    .orElse("");
            String report = """
                    project=%s
                    contentType=%s
                    package=%s
                    sourceRoot=%s
                    template=%s
                    qualifiedName=%s
                    parameters=%s
                    imports=%s
                    """.formatted(
                    config.projectNamespace(),
                    config.contentType(),
                    config.packageName(),
                    config.generatedSourcesRoot(),
                    template.name(),
                    template.fullyQualifiedClassName(),
                    parameters,
                    String.join(",", template.imports()));
            Path reportPath = config.generatedResourcesRoot().resolve("reports/" + template.name() + ".txt");
            try {
                Files.createDirectories(reportPath.getParent());
                Files.writeString(reportPath, report);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return List.of(reportPath);
        }
    }

    private record TestJteConfig(
            Path generatedSourcesRoot,
            Path generatedResourcesRoot,
            String projectNamespace,
            String packageName,
            ContentType contentType,
            ClassLoader classLoader) implements JteConfig {
    }

    private record TestTemplateDescription(
            String name,
            String packageName,
            String className,
            List<ParamDescription> params,
            List<String> imports) implements TemplateDescription {
    }

    private record TestParamDescription(String type, String name, String defaultValue) implements ParamDescription {
    }
}
