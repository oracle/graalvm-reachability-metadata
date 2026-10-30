/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClasspathResourceLoaderTest {
    @Test
    void reportsDeniedClasspathResourceAccess() throws Exception {
        ClasspathResourceLoader loader = new ClasspathResourceLoader();
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        ClassLoader restricted = new ClassLoader(original) {
            @Override
            public InputStream getResourceAsStream(String name) {
                throw new SecurityException("Resource access denied: " + name);
            }
        };
        try {
            thread.setContextClassLoader(restricted);
            ResourceNotFoundException failure = assertThrows(ResourceNotFoundException.class,
                    () -> loader.getResourceStream("velocity/greeting.vm"));
            assertTrue(failure.getMessage().contains("problem with template: velocity/greeting.vm"));
        } finally {
            thread.setContextClassLoader(original);
        }
        try (InputStream input = loader.getResourceStream("velocity/greeting.vm")) {
            assertEquals("Hello ${name}!\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void loadsClasspathTemplateAndItsParsedTemplate() throws Exception {
        VelocityEngine engine = VelocityTestSupport.engine();
        VelocityContext context = new VelocityContext();
        context.put("title", "Monthly");
        context.put("name", "Atlas");
        StringWriter output = new StringWriter();
        engine.getTemplate("velocity/page.vm", "UTF-8").merge(context, output);
        assertEquals("Title: Monthly\nHello Atlas!\n", output.toString());
        assertThrows(ResourceNotFoundException.class, () -> engine.getTemplate("velocity/absent.vm", "UTF-8"));
    }
}
