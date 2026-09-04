/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package gg.jte.generated.precompiled;

import static org.assertj.core.api.Assertions.assertThat;

import gg.jte.html.HtmlInterceptor;
import gg.jte.html.HtmlTemplateOutput;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.Writable;
import io.micronaut.http.HttpRequest;
import io.micronaut.views.jte.HtmlJteViewsRenderer;
import io.micronaut.views.jte.JteViewsRendererConfigurationProperties;
import io.micronaut.views.jte.PlainJteViewsRenderer;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A precompiled JTE fixture and its adapter integration tests. */
public class JtehelloGenerated {
    public static final String JTE_NAME = "hello.jte";
    public static final int[] JTE_LINE_INFO = {0};

    public static void render(
            HtmlTemplateOutput output, HtmlInterceptor interceptor, String name) {
        output.writeContent("<h1>Hello ");
        output.writeUserContent(name);
        output.writeContent("</h1>");
    }

    public static void renderMap(
            HtmlTemplateOutput output,
            HtmlInterceptor interceptor,
            Map<String, Object> parameters) {
        render(output, interceptor, (String) parameters.get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void htmlRendererDiscoversAndRendersPrecompiledTemplate() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            HtmlJteViewsRenderer<Map<String, Object>> renderer =
                    context.getBean(HtmlJteViewsRenderer.class);

            assertThat(renderer.exists("hello")).isTrue();
            assertThat(renderer.exists("missing")).isFalse();

            Writable writable =
                    renderer.render(
                            "hello",
                            Map.of("name", "<Micronaut>"),
                            HttpRequest.GET("/render"));
            StringWriter output = new StringWriter();
            writable.writeTo(output);

            assertThat(output.toString()).isEqualTo("<h1>Hello &lt;Micronaut&gt;</h1>");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void htmlRendererSupportsRenderingWithoutAModel() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            HtmlJteViewsRenderer<Object> renderer =
                    context.getBean(HtmlJteViewsRenderer.class);
            StringWriter output = new StringWriter();

            renderer.render("hello", null, null).writeTo(output);

            assertThat(output.toString()).isEqualTo("<h1>Hello </h1>");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void plainRendererWritesPrecompiledHtmlTemplateAsUtf8Text() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            PlainJteViewsRenderer<Map<String, Object>> renderer =
                    context.getBean(PlainJteViewsRenderer.class);
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            renderer.render("hello.jte", Map.of("name", "<Micronaut>"), null)
                    .writeTo(output, StandardCharsets.UTF_8);

            assertThat(output.toString(StandardCharsets.UTF_8))
                    .isEqualTo("<h1>Hello <Micronaut></h1>");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void writableHonorsNonUtf8Charset() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            HtmlJteViewsRenderer<Map<String, Object>> renderer =
                    context.getBean(HtmlJteViewsRenderer.class);
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            renderer.render("hello", Map.of("name", "Grüße"), null)
                    .writeTo(output, StandardCharsets.UTF_16LE);

            assertThat(output.toString(StandardCharsets.UTF_16LE))
                    .isEqualTo("<h1>Hello Grüße</h1>");
        }
    }

    @Test
    void configurationPropertiesExposeDefaultsAndUpdates() {
        JteViewsRendererConfigurationProperties configuration =
                new JteViewsRendererConfigurationProperties();

        assertThat(configuration.isDynamic()).isFalse();
        assertThat(configuration.getDynamicPath()).isEqualTo("build/jte-classes");
        assertThat(configuration.getDynamicSourcePath()).isNull();
        assertThat(configuration.isBinaryStaticContent()).isFalse();

        configuration.setDynamic(true);
        configuration.setDynamicPath("generated/jte");
        configuration.setDynamicSourcePath("templates");
        configuration.setBinaryStaticContent(true);

        assertThat(configuration.isDynamic()).isTrue();
        assertThat(configuration.getDynamicPath()).isEqualTo("generated/jte");
        assertThat(configuration.getDynamicSourcePath()).isEqualTo("templates");
        assertThat(configuration.isBinaryStaticContent()).isTrue();
    }
}
