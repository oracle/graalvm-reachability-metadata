/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package gg_jte.jte_native_resources;

import static org.assertj.core.api.Assertions.assertThat;

import gg.jte.ContentType;
import gg.jte.extension.api.JteConfig;
import gg.jte.extension.api.ParamDescription;
import gg.jte.extension.api.TemplateDescription;
import gg.jte.nativeimage.NativeResourcesExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Jte_native_resourcesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsExtensionName() {
        NativeResourcesExtension extension = new NativeResourcesExtension();

        assertThat(extension.name()).isEqualTo("native-image resource generator");
    }

    @Test
    void skipsGenerationWhenNoResourceOutputDirectoryIsConfigured() {
        NativeResourcesExtension extension = new NativeResourcesExtension();
        Set<TemplateDescription> templates = Set.of(template("page.jte", "example.views", "PageGenerated"));

        Collection<Path> generatedFiles = extension.generate(config(null, "example.application", "example.views"), templates);

        assertThat(generatedFiles).isEmpty();
    }

    @Test
    void skipsGenerationWhenThereAreNoTemplates() {
        NativeResourcesExtension extension = new NativeResourcesExtension();

        Collection<Path> generatedFiles = extension.generate(
                config(temporaryDirectory, "example.application", "example.views"), Set.of());

        assertThat(generatedFiles).isEmpty();
        assertThat(Files.exists(temporaryDirectory.resolve("META-INF"))).isFalse();
    }

    @Test
    void generatesNativeImageResourcesForAllTemplates() throws IOException {
        NativeResourcesExtension extension = new NativeResourcesExtension();
        Set<TemplateDescription> templates = new LinkedHashSet<>();
        templates.add(template("home.jte", "example.views", "HomeGenerated"));
        templates.add(template("page.jte", "example.layouts", "PageGenerated"));

        Collection<Path> generatedFiles = extension.generate(
                config(temporaryDirectory, "example.application", "fallback.package"), templates);

        Path resourceRoot = temporaryDirectory.resolve("META-INF/native-image/jte-generated/example.application");
        Path propertiesFile = resourceRoot.resolve("native-image.properties");
        Path resourcesFile = resourceRoot.resolve("resource-config.json");
        Path reflectionFile = resourceRoot.resolve("reflection-config.json");
        assertThat(generatedFiles).containsExactly(propertiesFile, resourcesFile, reflectionFile);
        assertThat(propertiesFile).isRegularFile();
        assertThat(resourcesFile).isRegularFile();
        assertThat(reflectionFile).isRegularFile();
        assertThat(Files.readString(propertiesFile)).isEqualTo("""
                Args = -H:ReflectionConfigurationResources=${.}/reflection-config.json -H:ResourceConfigurationResources=${.}/resource-config.json
                """);
        assertThat(Files.readString(resourcesFile)).isEqualTo("""
                {"resources": {"includes": [{"pattern": ".*Generated\\\\.bin$"}]}}
                """);
        assertThat(Files.readString(reflectionFile)).isEqualTo("""
                [
                {"name":"example.views.HomeGenerated", "allDeclaredMethods":true, "allDeclaredFields":true},
                {"name":"example.layouts.PageGenerated", "allDeclaredMethods":true, "allDeclaredFields":true}
                ]
                """);
    }

    @Test
    void usesConfiguredPackageWhenProjectNamespaceIsAbsent() throws IOException {
        NativeResourcesExtension extension = new NativeResourcesExtension();
        Set<TemplateDescription> templates = Set.of(template("card.jte", "example.cards", "CardGenerated"));

        Collection<Path> generatedFiles = extension.generate(
                config(temporaryDirectory, null, "fallback.application"), templates);

        Path resourceRoot = temporaryDirectory.resolve("META-INF/native-image/jte-generated/fallback.application");
        assertThat(generatedFiles).containsExactly(
                resourceRoot.resolve("native-image.properties"),
                resourceRoot.resolve("resource-config.json"),
                resourceRoot.resolve("reflection-config.json"));
        assertThat(Files.readString(resourceRoot.resolve("reflection-config.json"))).isEqualTo("""
                [
                {"name":"example.cards.CardGenerated", "allDeclaredMethods":true, "allDeclaredFields":true}
                ]
                """);
    }

    private static JteConfig config(Path generatedResourcesRoot, String projectNamespace, String packageName) {
        return new TestJteConfig(generatedResourcesRoot, projectNamespace, packageName);
    }

    private static TemplateDescription template(String name, String packageName, String className) {
        return new TestTemplateDescription(name, packageName, className);
    }

    private record TestTemplateDescription(String name, String packageName, String className)
            implements TemplateDescription {
        @Override
        public List<ParamDescription> params() {
            return List.of();
        }

        @Override
        public List<String> imports() {
            return List.of();
        }
    }

    private record TestJteConfig(Path generatedResourcesRoot, String projectNamespace, String packageName)
            implements JteConfig {
        @Override
        public Path generatedSourcesRoot() {
            return generatedResourcesRoot == null ? null : generatedResourcesRoot.resolveSibling("generated-sources");
        }

        @Override
        public ContentType contentType() {
            return ContentType.Html;
        }

        @Override
        public ClassLoader classLoader() {
            return Jte_native_resourcesTest.class.getClassLoader();
        }
    }
}
