/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package gg_jte.jte_runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.TemplateException;
import gg.jte.output.StringOutput;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Jte_runtimeTest {
    @Test
    void invokesNoParameterRenderMethodThroughTemplateEngine() {
        TemplateEngine engine = precompiledEngine();
        StringOutput output = new StringOutput();

        engine.render(
                JteJte_runtimeNoParametersTestGenerated.templateName(), (Object) null, output);

        assertThat(output.toString()).isEqualTo("rendered without parameters");
    }

    @Test
    void invokesParameterRenderMethodThroughTemplateEngine() {
        TemplateEngine engine = precompiledEngine();
        StringOutput output = new StringOutput();

        engine.render(JteJte_runtimeParametersTestGenerated.templateName(), "value", output);

        assertThat(output.toString()).isEqualTo("parameter: value");
    }

    @Test
    void invokesMapRenderMethodThroughTemplateEngine() {
        TemplateEngine engine = precompiledEngine();
        StringOutput output = new StringOutput();

        engine.render(
                JteJte_runtimeParametersTestGenerated.templateName(),
                Map.of("value", "mapped"),
                output);

        assertThat(output.toString()).isEqualTo("map: mapped");
    }

    @Test
    void reportsPrecompiledTemplateNameWhenRenderingFails() {
        TemplateEngine engine = TemplateEngine.createPrecompiled(
                null, ContentType.Plain, Jte_runtimeTest.class.getClassLoader(), "gg_jte.jte_runtime");

        TemplateException exception = assertThrows(
                TemplateException.class,
                () -> engine.render(JteJte_runtimeTestGenerated.templateName(), (Object) null, new StringOutput()));

        assertThat(exception)
                .hasMessageContaining("welcome.jte")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private static TemplateEngine precompiledEngine() {
        return TemplateEngine.createPrecompiled(
                null,
                ContentType.Plain,
                Jte_runtimeTest.class.getClassLoader(),
                "gg_jte.jte_runtime");
    }
}
