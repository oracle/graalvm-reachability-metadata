/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.app.VelocityEngine;
import io.sundr.deps.org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import io.sundr.deps.org.apache.velocity.runtime.resource.util.StringResourceRepository;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

public class StringResourceLoaderTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityEngine engine = TestSupport.engine();
        engine.setProperty("resource.loader", "string");
        engine.setProperty("string.resource.loader.class", StringResourceLoader.class.getName());
        engine.setProperty("string.resource.loader.repository.static", false);
        engine.setProperty("string.resource.loader.repository.name", "templates");
        engine.init();
        StringResourceRepository repository = (StringResourceRepository) engine.getApplicationAttribute("templates");
        repository.putStringResource("greeting", "Hello $name");
        VelocityContext context = new VelocityContext();
        context.put("name", "Ada");
        StringWriter output = new StringWriter();
        engine.getTemplate("greeting").merge(context, output);
        assertEquals("Hello Ada", output.toString());
    }

    @Test
    void reloadsUpdatedStringTemplatesAndRemovesResources() {
        VelocityEngine engine = TestSupport.engine();
        engine.setProperty("resource.loader", "string");
        engine.setProperty("string.resource.loader.class", StringResourceLoader.class.getName());
        engine.setProperty("string.resource.loader.repository.static", false);
        engine.setProperty("string.resource.loader.repository.name", "editable-templates");
        engine.init();
        StringResourceRepository repository = (StringResourceRepository) engine.getApplicationAttribute("editable-templates");
        VelocityContext context = new VelocityContext();
        context.put("name", "Ada");
        repository.putStringResource("editable", "First $name");
        StringWriter first = new StringWriter();
        engine.getTemplate("editable").merge(context, first);
        assertEquals("First Ada", first.toString());
        repository.putStringResource("editable", "Updated $name");
        StringWriter updated = new StringWriter();
        engine.getTemplate("editable").merge(context, updated);
        assertEquals("Updated Ada", updated.toString());
        repository.removeStringResource("editable");
        assertFalse(engine.resourceExists("editable"));
    }
}
