/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.velocity.util.ClassUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class VelocityClassUtilsTest {
    @Test
    void exercisesPublicApi() throws Exception {
        assertEquals(StringBuilder.class, ClassUtils.getClass("java.lang.StringBuilder"));
        StringBuilder builder = (StringBuilder) ClassUtils.getNewInstance("java.lang.StringBuilder");
        builder.append("created");
        assertEquals("created", builder.toString());
        try (InputStream stream = ClassUtils.getResourceAsStream(VelocityClassUtilsTest.class, "fixtures/settings.properties")) {
            assertNotNull(stream);
            assertTrue(new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("greeting=hello"));
        }
    }
}
