/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javac;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Javac13Test {
    @Test
    void compilesSourceFileThroughModernCompilerAdapter(@TempDir Path temporaryDirectory) throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("src");
        Path destinationDirectory = temporaryDirectory.resolve("classes");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(destinationDirectory);
        Files.write(
            sourceDirectory.resolve("GeneratedGreeting.java"),
            generatedGreetingSource().getBytes(StandardCharsets.UTF_8));

        Javac javac = newModernJavacTask(sourceDirectory, destinationDirectory);

        javac.execute();

        assertThat(destinationDirectory.resolve("GeneratedGreeting.class")).exists();
    }

    private static Javac newModernJavacTask(Path sourceDirectory, Path destinationDirectory) {
        Project project = new Project();
        project.init();
        project.setBaseDir(sourceDirectory.getParent().toFile());

        Javac javac = new Javac();
        javac.setProject(project);
        javac.setTaskName("javac");
        javac.setCompiler("javac1.3");
        javac.setSrcdir(new org.apache.tools.ant.types.Path(project, sourceDirectory.toString()));
        javac.setDestdir(destinationDirectory.toFile());
        javac.setIncludeantruntime(false);
        javac.setIncludejavaruntime(false);
        return javac;
    }

    private static String generatedGreetingSource() {
        return "public class GeneratedGreeting {\n"
            + "    public String message() {\n"
            + "        return \"hello\";\n"
            + "    }\n"
            + "}\n";
    }
}
