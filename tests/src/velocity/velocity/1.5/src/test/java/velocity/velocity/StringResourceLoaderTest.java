/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.Properties;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.junit.jupiter.api.Test;

public class StringResourceLoaderTest {
    @Test
    public void rendersTemplateFromStringResourceRepository() throws Exception {
        String templateName = "string-resource-loader-template.vm";
        Properties properties = new Properties();
        properties.setProperty(RuntimeConstants.RESOURCE_LOADER, "string");
        properties.setProperty("string.resource.loader.class", StringResourceLoader.class.getName());
        properties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, NullLogChute.class.getName());

        VelocityEngine engine = new VelocityEngine();
        engine.init(properties);

        StringResourceLoader.getRepository()
                .putStringResource(templateName, "Hello $name from the string repository");
        VelocityContext context = new VelocityContext();
        context.put("name", "Velocity");
        StringWriter writer = new StringWriter();

        Template template = engine.getTemplate(templateName);
        template.merge(context, writer);

        assertThat(writer.toString()).isEqualTo("Hello Velocity from the string repository");
    }
}
