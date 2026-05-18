/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.texen.Generator;
import org.apache.velocity.texen.util.FileUtil;
import org.apache.velocity.texen.util.PropertiesUtil;
import org.apache.velocity.util.StringUtils;
import org.junit.jupiter.api.Test;

public class GeneratorTest {
    @Test
    public void parsesControlTemplateWithDefaultTexenContextObjects() throws Exception {
        TexenGenerator generator = new TexenGenerator("missing-texen-test.properties");
        generator.setOutputPath("target/texen-output");
        clearVelocityEngineClassCache();
        generator.reloadDefaultProperties();
        assertThat(resolveVelocityEngineThroughGeneratedHelper())
                .isSameAs(VelocityEngine.class);

        VelocityContext context = new VelocityContext();
        String rendered = generator.parse("control.vm", context);

        assertThat(rendered).isEqualTo("control.vm");
        assertThat(context.get("generator")).isSameAs(Generator.getInstance());
        assertThat(context.get("outputDirectory")).isEqualTo("target/texen-output");
        assertThat(context.get("strings")).isInstanceOf(StringUtils.class);
        assertThat(context.get("files")).isInstanceOf(FileUtil.class);
        assertThat(context.get("properties")).isInstanceOf(PropertiesUtil.class);
    }

    private static void clearVelocityEngineClassCache() throws Exception {
        Field classCache = Generator.class.getDeclaredField(
                "class$org$apache$velocity$app$VelocityEngine");
        classCache.setAccessible(true);
        classCache.set(null, null);
    }

    private static Class<?> resolveVelocityEngineThroughGeneratedHelper() throws Exception {
        Method generatedClassLiteralHelper = Generator.class.getDeclaredMethod(
                "class$", String.class);
        generatedClassLiteralHelper.setAccessible(true);
        return (Class<?>) generatedClassLiteralHelper.invoke(
                null, "org.apache.velocity.app.VelocityEngine");
    }

    private static final class TexenGenerator extends Generator {
        private TexenGenerator(String propFile) {
            super(propFile);
        }

        private void reloadDefaultProperties() {
            setDefaultProps();
        }

        @Override
        public Template getTemplate(String templateName, String encoding) {
            return new ControlTemplate(templateName);
        }
    }

    private static final class ControlTemplate extends Template {
        private final String templateName;

        private ControlTemplate(String templateName) {
            this.templateName = templateName;
        }

        @Override
        public void merge(Context context, Writer writer) throws IOException {
            writer.write(templateName);
        }
    }
}
