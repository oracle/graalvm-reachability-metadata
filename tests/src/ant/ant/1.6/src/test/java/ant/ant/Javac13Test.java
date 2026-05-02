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
import org.apache.tools.ant.taskdefs.compilers.Javac13;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class Javac13Test {
    @TempDir
    Path temporaryDirectory;

    @Test
    void executesModernCompilerThroughAdapter() throws IOException {
        Javac13 compiler = new Javac13();
        compiler.setJavac(newJavacTask());

        try {
            boolean compilationSucceeded = compiler.execute();

            assertThat(compilationSucceeded).isFalse();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private Javac newJavacTask() throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path destinationDirectory = temporaryDirectory.resolve("classes");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(destinationDirectory);

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
