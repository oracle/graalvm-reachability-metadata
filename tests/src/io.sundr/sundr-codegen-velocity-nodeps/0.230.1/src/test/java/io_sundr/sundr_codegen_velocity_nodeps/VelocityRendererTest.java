/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.codegen.velocity.VelocityRenderer;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class VelocityRendererTest {
    private static final String TEMPLATE_RESOURCE = ""
            + "/io_sundr/sundr_codegen_velocity_nodeps/velocity-renderer-template.vm";

    @Test
    void loadsTemplateResource() {
        Optional<VelocityRenderer<?>> renderer = VelocityRenderer.fromTemplateResource(
                TEMPLATE_RESOURCE,
                "first",
                "second");

        assertThat(renderer).isPresent();
    }

    @Test
    void loadsTypedTemplateResourceAndRendersModel() {
        Optional<VelocityRenderer<String>> renderer = VelocityRenderer.fromTemplateResource(
                TEMPLATE_RESOURCE,
                String.class,
                "compiler");

        String output = renderer.orElseThrow().render("Grace");

        assertThat(output).isEqualTo("Hello Grace [compiler]");
    }
}
