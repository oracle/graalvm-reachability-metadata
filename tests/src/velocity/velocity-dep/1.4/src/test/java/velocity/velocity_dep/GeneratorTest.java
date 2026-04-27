/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.texen.Generator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesControlTemplateWithDefaultTexenContextObjects() throws Exception {
        Path templateDirectory = temporaryDirectory.resolve("templates");
        Path outputDirectory = temporaryDirectory.resolve("generated");
        Files.createDirectories(templateDirectory);
        Files.createDirectories(outputDirectory);
        Files.write(
                templateDirectory.resolve("control.vm"),
                "$strings.firstLetterCaps($word)|$files.file($fileName).name|$properties.load($propertiesFile).getProperty($propertyKey)|$outputDirectory"
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                templateDirectory.resolve("sample.properties"),
                "greeting=hello from texen\n".getBytes(StandardCharsets.UTF_8));

        Generator generator = Generator.getInstance();
        generator.setVelocityEngine(createVelocityEngine(templateDirectory));
        generator.setTemplatePath(templateDirectory.toString());
        generator.setOutputPath(outputDirectory.toString());

        VelocityContext context = new VelocityContext();
        context.put("word", "velocity");
        context.put("fileName", "report.txt");
        context.put("propertiesFile", "sample.properties");
        context.put("propertyKey", "greeting");

        String output = generator.parse("control.vm", context);

        assertThat(output).isEqualTo("Velocity|report.txt|hello from texen|" + outputDirectory);
    }

    private static VelocityEngine createVelocityEngine(Path templateDirectory) throws Exception {
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        engine.setProperty(RuntimeConstants.VM_LIBRARY, "");
        engine.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, templateDirectory.toString());
        engine.init();
        return engine;
    }
}
