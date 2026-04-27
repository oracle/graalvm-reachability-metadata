/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.tools.ant.Project;
import org.apache.velocity.texen.ant.TexenTask;
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
    void texenTaskParsesControlTemplateWithDefaultGeneratorContextObjects() throws Exception {
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
        Path contextProperties = templateDirectory.resolve("context.properties");
        Files.write(
                contextProperties,
                ("word=velocity\n"
                        + "fileName=report.txt\n"
                        + "propertiesFile=sample.properties\n"
                        + "propertyKey=greeting\n").getBytes(StandardCharsets.UTF_8));

        TexenTask task = new TexenTask();
        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());
        task.setProject(project);
        task.setTemplatePath(templateDirectory.toString());
        task.setContextProperties(contextProperties.toString());
        task.setOutputDirectory(outputDirectory.toFile());
        task.setOutputFile("result.txt");
        task.setControlTemplate("control.vm");

        task.execute();

        assertThat(outputDirectory.resolve("result.txt"))
                .content(StandardCharsets.UTF_8)
                .isEqualTo("Velocity|report.txt|hello from texen|" + outputDirectory.toRealPath());
    }
}
