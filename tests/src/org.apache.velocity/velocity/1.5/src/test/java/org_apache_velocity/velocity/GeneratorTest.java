/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.texen.Generator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class GeneratorTest {
    @TempDir
    Path outputDirectory;

    @Test
    void populatesDefaultToolsAndGeneratesTextAndFiles() throws Exception {
        Generator generator = Generator.getInstance();
        generator.setVelocityEngine(VelocityTestSupport.engine());
        generator.setOutputPath(outputDirectory.toString());
        generator.setInputEncoding("UTF-8");
        generator.setOutputEncoding("UTF-8");
        try {
            VelocityContext context = new VelocityContext();
            context.put("name", "atlas");
            assertEquals("Report for Atlas\n", generator.parse("velocity/texen-control.vm", context));
            assertSame(generator, context.get("generator"));
            assertEquals(outputDirectory.toString(), context.get("outputDirectory"));
            assertEquals("", generator.parse("velocity/greeting.vm", "greeting.txt", "name", "Orion"));
        } finally {
            generator.shutdown();
        }
        assertEquals("Hello Orion!\n", Files.readString(outputDirectory.resolve("greeting.txt")));
    }
}
