/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.texen.Generator;
import org.apache.velocity.texen.util.FileUtil;
import org.apache.velocity.texen.util.PropertiesUtil;
import org.apache.velocity.util.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GeneratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesControlTemplateWithDefaultTexenContextObjects() throws Exception {
        final Path templateDirectory = temporaryDirectory.resolve("templates");
        final Path outputDirectory = temporaryDirectory.resolve("generated");
        Files.createDirectories(templateDirectory);
        Files.createDirectories(outputDirectory);
        Files.writeString(
                templateDirectory.resolve("control.vm"),
                "Generated $name in $outputDirectory",
                StandardCharsets.UTF_8);

        final VelocityEngine engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        engine.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, templateDirectory.toString());
        engine.setProperty(RuntimeConstants.VM_LIBRARY, "");
        engine.init();

        final Generator generator = new Generator(temporaryDirectory.resolve("missing-texen.properties").toString());
        generator.setVelocityEngine(engine);
        generator.setTemplatePath(templateDirectory.toString());
        generator.setOutputPath(outputDirectory.toString());

        final VelocityContext context = new VelocityContext();
        context.put("name", "Texen");

        final String output = generator.parse("control.vm", context);

        assertThat(output).isEqualTo("Generated Texen in " + outputDirectory);
        assertThat(context.get("generator")).isSameAs(Generator.getInstance());
        assertThat(context.get("outputDirectory")).isEqualTo(outputDirectory.toString());
        assertThat(context.get("strings")).isInstanceOf(StringUtils.class);
        assertThat(context.get("files")).isInstanceOf(FileUtil.class);
        assertThat(context.get("properties")).isInstanceOf(PropertiesUtil.class);
    }
}
