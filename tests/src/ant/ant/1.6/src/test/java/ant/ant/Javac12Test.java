/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.taskdefs.compilers.Javac12;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sun.tools.javac.Main;

import static org.assertj.core.api.Assertions.assertThat;

public class Javac12Test {
    @TempDir
    Path temporaryDirectory;

    private Path sourceDirectory;
    private Path destinationDirectory;

    @Test
    void executesClassicCompilerThroughAdapter() throws IOException {
        Main.reset();
        Javac12 compiler = new Javac12();
        compiler.setJavac(newJavacTask());

        try {
            boolean compilationSucceeded = compiler.execute();

            assertThat(compilationSucceeded).isTrue();
            assertThat(Main.invocationCount()).isEqualTo(1);
            assertThat(Main.lastCompilerName()).isEqualTo("javac");
            assertThat(Main.lastArguments())
                    .contains("-d", destinationDirectory.toString())
                    .contains(sourceDirectory.toString());
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private Javac newJavacTask() throws IOException {
        sourceDirectory = temporaryDirectory.resolve("sources");
        destinationDirectory = temporaryDirectory.resolve("classes");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(destinationDirectory);
        Files.writeString(sourceDirectory.resolve("Example.java"), "class Example { }\n");

        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());
        project.init();

        Javac javac = new Javac();
        javac.setProject(project);
        javac.setTaskName("javac");
        javac.createSrc().setLocation(sourceDirectory.toFile());
        javac.setDestdir(destinationDirectory.toFile());
        javac.setIncludeantruntime(false);
        javac.setIncludejavaruntime(false);
        return javac;
    }
}
