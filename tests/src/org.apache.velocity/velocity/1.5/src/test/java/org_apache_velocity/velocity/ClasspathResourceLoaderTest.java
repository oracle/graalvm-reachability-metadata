/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClasspathResourceLoaderTest {
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
