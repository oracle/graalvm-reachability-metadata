/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.app.VelocityEngine;
import io.sundr.deps.org.apache.velocity.exception.ResourceNotFoundException;
import io.sundr.deps.org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import java.io.InputStream;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

public class ClasspathResourceLoaderTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VelocityEngine engine = TestSupport.classpathEngine();
        VelocityContext context = new VelocityContext();
        context.put("name", "Ada");
        StringWriter output = new StringWriter();
        engine.getTemplate("fixtures/greeting.vm", "UTF-8").merge(context, output);
        assertEquals("Hello Ada", output.toString());
        assertTrue(engine.resourceExists("fixtures/greeting.vm"));
    }

    @Test
    void preservesResourceAccessDenialAsTheCause() {
        String resource = "fixtures/greeting.vm";
        SecurityException denial = new SecurityException("Template resource access denied");
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        ClassLoader resourcePolicy = new ClassLoader(original) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (resource.equals(name)) {
                    throw denial;
                }
                return super.getResourceAsStream(name);
            }
        };
        try {
            thread.setContextClassLoader(resourcePolicy);
            ClasspathResourceLoader loader = new ClasspathResourceLoader();
            ResourceNotFoundException failure = assertThrows(ResourceNotFoundException.class,
                    () -> loader.getResourceStream(resource));
            assertSame(denial, failure.getCause());
            assertTrue(failure.getMessage().contains(resource));
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
