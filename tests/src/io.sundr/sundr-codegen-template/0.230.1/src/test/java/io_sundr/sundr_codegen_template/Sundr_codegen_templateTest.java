/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.codegen.template.TemplateRenderer;
import io.sundr.codegen.template.TemplateRendererFactory;
import io.sundr.codegen.template.TemplateRenderers;
import io.sundr.codegen.template.utils.Templates;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Sundr_codegen_templateTest {
    @TempDir
    Path tempDir;

    @Test
    void readsTemplateContentFromUrl() throws IOException {
        Path template = tempDir.resolve("greeting.template");
        Files.writeString(template, "hello\ntemplate", StandardCharsets.UTF_8);

        String content = Templates.read(template.toUri().toURL());

        assertThat(content).isEqualTo("hello" + System.lineSeparator() + "template");
    }

    @Test
    void resolvesExtensionsFromTemplateNamesAndUrls() throws IOException {
        Path template = tempDir.resolve("person.java.vm");
        Files.writeString(template, "ignored", StandardCharsets.UTF_8);

        assertThat(Templates.getExtension("person.java.vm")).contains("vm");
        assertThat(Templates.getExtension(template.toUri().toURL())).contains("vm");
        assertThat(Templates.getExtension("template-without-extension")).isEmpty();
        assertThat(Templates.getExtension("")).isEmpty();
    }

    @Test
    void factoryAcceptsUrlsWithConfiguredTemplateExtension() throws IOException {
        TemplateRendererFactory factory = new StringTemplateRendererFactory("tmpl", "vm");
        Path velocityTemplate = tempDir.resolve("type.vm");
        Path unsupportedTemplate = tempDir.resolve("type.txt");
        Files.writeString(velocityTemplate, "ignored", StandardCharsets.UTF_8);
        Files.writeString(unsupportedTemplate, "ignored", StandardCharsets.UTF_8);

        assertThat(factory.getTemplateExtensions()).containsExactly("tmpl", "vm");
        assertThat(factory.accepts(velocityTemplate.toUri().toURL())).isTrue();
        assertThat(factory.accepts(unsupportedTemplate.toUri().toURL())).isFalse();
    }

    @Test
    void factoryWithoutExtensionsRejectsTemplateUrls() throws IOException {
        TemplateRendererFactory factory = new EmptyTemplateRendererFactory();
        Path template = tempDir.resolve("type.vm");
        Files.writeString(template, "ignored", StandardCharsets.UTF_8);

        assertThat(factory.getTemplateExtensions()).isEmpty();
        assertThat(factory.accepts(template.toUri().toURL())).isFalse();
    }

    @Test
    void rendererCreatedByFactoryUsesTemplateContentsAndArguments() throws IOException {
        TemplateRendererFactory factory = new StringTemplateRendererFactory("tmpl");
        Path template = tempDir.resolve("message.tmpl");
        Files.writeString(template, "Hello {{name}}!", StandardCharsets.UTF_8);

        TemplateRenderer<String> renderer = factory.create(String.class, template.toUri().toURL(), "<", ">");

        assertThat(renderer.getType()).isEqualTo(String.class);
        assertThat(renderer.render("Sundr")).isEqualTo("<Hello Sundr!>");
        assertThat(renderer.getFunction().apply("Template")).isEqualTo("<Hello Template!>");
    }

    @Test
    void templateRenderersReturnsEmptyWhenNoRegisteredFactoryAcceptsTemplate() throws IOException {
        Path template = tempDir.resolve("message.unsupported");
        Files.writeString(template, "ignored", StandardCharsets.UTF_8);

        Optional<TemplateRenderer<String>> renderer = TemplateRenderers.getTemplateRenderer(
                String.class, template.toUri().toURL());

        assertThat(renderer).isEmpty();
    }

    @Test
    void readsTemplateContentFromJarUrl() throws IOException {
        Path archive = tempDir.resolve("templates.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(archive))) {
            jar.putNextEntry(new JarEntry("templates/greeting.vm"));
            jar.write("Hello from archive".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        URL template = URI.create("jar:" + archive.toUri() + "!/templates/greeting.vm").toURL();

        assertThat(Templates.getExtension(template)).contains("vm");
        assertThat(Templates.read(template)).isEqualTo("Hello from archive");
    }

    @Test
    void readWrapsMissingTemplateIoFailures() throws IOException {
        URL missingTemplate = tempDir.resolve("missing.vm").toUri().toURL();

        assertThatThrownBy(() -> Templates.read(missingTemplate)).isInstanceOf(RuntimeException.class);
    }

    private static final class EmptyTemplateRendererFactory extends TemplateRendererFactory {
        @Override
        public <T> TemplateRenderer<T> create(Class<T> type, URL template, String... arguments) {
            return new SimpleTemplateRenderer<>(type, value -> String.valueOf(value));
        }
    }

    private static final class StringTemplateRendererFactory extends TemplateRendererFactory {
        private final String[] extensions;

        private StringTemplateRendererFactory(String... extensions) {
            this.extensions = extensions;
        }

        @Override
        public String[] getTemplateExtensions() {
            return extensions;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> TemplateRenderer<T> create(Class<T> type, URL template, String... arguments) {
            String prefix = arguments.length > 0 ? arguments[0] : "";
            String suffix = arguments.length > 1 ? arguments[1] : "";
            String templateContent = Templates.read(template);
            Function<String, String> renderer = value -> prefix + templateContent.replace("{{name}}", value) + suffix;
            return (TemplateRenderer<T>) new SimpleTemplateRenderer<>(String.class, renderer);
        }
    }

    private static final class SimpleTemplateRenderer<T> extends TemplateRenderer<T> {
        private final Class<T> type;
        private final Function<T, String> function;

        private SimpleTemplateRenderer(Class<T> type, Function<T, String> function) {
            this.type = type;
            this.function = function;
        }

        @Override
        public Class<T> getType() {
            return type;
        }

        @Override
        public Function<T, String> getFunction() {
            return function;
        }
    }
}
