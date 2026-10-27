/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package gg_jte.jte_runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gg.jte.TemplateEngine;
import gg.jte.TemplateException;
import gg.jte.output.StringOutput;
import org.junit.jupiter.api.Test;

public class RuntimeTemplateLoaderTest {
    @Test
    void reportsPrecompiledTemplateNameWhenRenderingFails() {
        TemplateEngine engine = Jte_runtimeTest.precompiledEngine();

        TemplateException exception = assertThrows(
                TemplateException.class,
                () -> engine.render(
                        JteJte_runtimeTestGenerated.templateName(),
                        (Object) null,
                        new StringOutput()));

        assertThat(exception)
                .hasMessageContaining("welcome.jte")
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
