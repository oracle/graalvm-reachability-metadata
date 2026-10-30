/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.io.StringWriter;
import java.util.List;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringResourceLoaderTest {
    @Test
    void rendersTemplateRegisteredInStringRepository() throws Exception {
        VelocityEngine engine = VelocityTestSupport.configuredEngine();
        engine.setProperty("resource.loader", "string");
        engine.setProperty("string.resource.loader.class", StringResourceLoader.class.getName());
        engine.init();
        StringResourceRepository repository = StringResourceLoader.getRepository();
        repository.putStringResource("memory-report.vm", "Report for $name: #foreach($item in $items)$item;#end");
        try {
            VelocityContext context = new VelocityContext();
            context.put("name", "Atlas");
            context.put("items", List.of("one", "two"));
            StringWriter output = new StringWriter();
            engine.getTemplate("memory-report.vm", "UTF-8").merge(context, output);
            assertEquals("Report for Atlas: one;two;", output.toString());
        } finally {
            repository.removeStringResource("memory-report.vm");
        }
    }
}
