/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.app.VelocityEngine;
import io.sundr.deps.org.apache.velocity.runtime.resource.loader.URLResourceLoader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class URLResourceLoaderTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Files.writeString(directory.resolve("greeting.vm"), "Hello $name");
        VelocityEngine engine = TestSupport.engine();
        engine.setProperty("resource.loader", "url");
        engine.setProperty("url.resource.loader.class", URLResourceLoader.class.getName());
        engine.setProperty("url.resource.loader.root", directory.toUri().toString());
        engine.setProperty("url.resource.loader.timeout", 10000);
        engine.init();
        VelocityContext context = new VelocityContext();
        context.put("name", "Ada");
        StringWriter output = new StringWriter();
        engine.getTemplate("greeting.vm").merge(context, output);
        assertEquals("Hello Ada", output.toString());
    }

    @TempDir
    Path directory;
}
