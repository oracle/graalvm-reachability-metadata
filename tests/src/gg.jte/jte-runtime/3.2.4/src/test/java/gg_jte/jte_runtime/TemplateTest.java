/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package gg_jte.jte_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TemplateTest {
    @Test
    void invokesNoParameterRenderMethodThroughTemplateEngine() {
        TemplateEngine engine = Jte_runtimeTest.precompiledEngine();
        StringOutput output = new StringOutput();

        engine.render(
                JteJte_runtimeNoParametersTestGenerated.templateName(), (Object) null, output);

        assertThat(output.toString()).isEqualTo("rendered without parameters");
    }

    @Test
    void invokesParameterRenderMethodThroughTemplateEngine() {
        TemplateEngine engine = Jte_runtimeTest.precompiledEngine();
        StringOutput output = new StringOutput();

        engine.render(JteJte_runtimeParametersTestGenerated.templateName(), "value", output);

        assertThat(output.toString()).isEqualTo("parameter: value");
    }

    @Test
    void invokesMapRenderMethodThroughTemplateEngine() {
        TemplateEngine engine = Jte_runtimeTest.precompiledEngine();
        StringOutput output = new StringOutput();

        engine.render(
                JteJte_runtimeParametersTestGenerated.templateName(),
                Map.of("value", "mapped"),
                output);

        assertThat(output.toString()).isEqualTo("map: mapped");
    }
}
