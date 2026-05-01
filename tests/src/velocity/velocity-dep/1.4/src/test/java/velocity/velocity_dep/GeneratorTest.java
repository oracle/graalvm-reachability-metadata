/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.io.Writer;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.texen.Generator;
import org.apache.velocity.texen.util.FileUtil;
import org.apache.velocity.texen.util.PropertiesUtil;
import org.apache.velocity.util.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratorTest {
    @Test
    void parsesControlTemplateWithDefaultTexenContextObjects() throws Exception {
        Generator generator = new Generator("missing-texen.properties");
        generator.setVelocityEngine(new ConstantTemplateVelocityEngine());
        generator.setOutputPath("target/generated-texen");

        VelocityContext context = new VelocityContext();
        String rendered = generator.parse("control.vm", context);

        assertEquals("texen template rendered", rendered);
        assertSame(Generator.getInstance(), context.get("generator"));
        assertEquals("target/generated-texen", context.get("outputDirectory"));
        assertTrue(context.get("strings") instanceof StringUtils);
        assertTrue(context.get("files") instanceof FileUtil);
        assertTrue(context.get("properties") instanceof PropertiesUtil);
    }
}

class ConstantTemplateVelocityEngine extends VelocityEngine {
    @Override
    public Template getTemplate(String name) {
        return new ConstantTemplate();
    }

    @Override
    public Template getTemplate(String name, String encoding) {
        return getTemplate(name);
    }
}

class ConstantTemplate extends Template {
    @Override
    public void merge(Context context, Writer writer) throws Exception {
        writer.write("texen template rendered");
    }
}
