/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.codegen.velocity.VelocityRenderer;
import org.junit.jupiter.api.Test;

public class VelocityRendererTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityRenderer<String> typed = VelocityRenderer.fromTemplateResource("/fixtures/model.vm", String.class).orElseThrow();
        assertEquals("Model: Ada", typed.getFunction().apply("Ada"));
        VelocityRenderer<?> untyped = VelocityRenderer.fromTemplateResource("/fixtures/parameters.vm", "first").orElseThrow();
        assertEquals("Parameter: first", untyped.getFunction().apply(null));
    }
}
