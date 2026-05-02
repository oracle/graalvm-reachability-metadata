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
import org.apache.tools.ant.taskdefs.rmic.WLRmic;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import weblogic.rmic;

import static org.assertj.core.api.Assertions.assertThat;

public class WLRmicTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void executeLoadsWebLogicRmicImplementationAndRunsIt() {
        rmic.reset();
        WLRmic adapter = new WLRmic();
        adapter.setRmic(newRmic());

        try {
            boolean completed = adapter.execute();

            assertThat(completed).isTrue();
            assertThat(rmic.invocationCount()).isEqualTo(1);
            assertThat(rmic.lastArguments())
                    .contains("-noexit", "-d", temporaryDirectory.toString(), "-classpath");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private Rmic newRmic() {
        Project project = new Project();
        project.init();

        Rmic rmicTask = new Rmic();
        rmicTask.setProject(project);
        rmicTask.setTaskName("rmic");
        rmicTask.setBase(temporaryDirectory.toFile());
        return rmicTask;
    }
}
