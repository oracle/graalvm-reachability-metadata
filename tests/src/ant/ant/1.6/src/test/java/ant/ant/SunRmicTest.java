/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Rmic;
import org.apache.tools.ant.taskdefs.rmic.SunRmic;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sun.rmi.rmic.Main;

import static org.assertj.core.api.Assertions.assertThat;

public class SunRmicTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void executeLoadsSunRmicImplementationAndRunsIt() {
        Main.reset();
        SunRmic adapter = new SunRmic();
        adapter.setRmic(newRmic());

        try {
            boolean completed = adapter.execute();

            assertThat(completed).isTrue();
            assertThat(Main.invocationCount()).isEqualTo(1);
            assertThat(Main.lastCompilerName()).isEqualTo("rmic");
            assertThat(Main.lastArguments())
                    .contains("-d", temporaryDirectory.toString(), "-classpath");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private Rmic newRmic() {
        Project project = new Project();
        project.init();

        Rmic rmic = new Rmic();
        rmic.setProject(project);
        rmic.setTaskName("rmic");
        rmic.setBase(temporaryDirectory.toFile());
        return rmic;
    }
}
