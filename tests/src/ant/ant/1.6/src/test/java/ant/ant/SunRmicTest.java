/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.rmi.Remote;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Rmic;
import org.apache.tools.ant.taskdefs.rmic.SunRmic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.rmi.rmic.Main;

public class SunRmicTest {
    @Test
    void executesSunRmicAdapterWithOutputStreamConstructor(@TempDir Path baseDirectory) {
        Main.reset();
        SunRmic adapter = new SunRmic();
        adapter.setRmic(newRmic(baseDirectory));

        boolean executed = adapter.execute();

        assertThat(executed).isTrue();
        assertThat(Main.getLastProgramName()).isEqualTo("rmic");
        assertThat(Main.getLastArguments())
                .containsSubsequence("-d", baseDirectory.toFile().getAbsolutePath())
                .contains("-classpath", ExampleRemoteService.class.getName());
    }

    private static Rmic newRmic(Path baseDirectory) {
        Project project = new Project();
        project.init();

        Rmic rmic = new Rmic();
        rmic.setProject(project);
        rmic.setTaskName("rmic");
        rmic.setBase(baseDirectory.toFile());
        rmic.getCompileList().addElement(ExampleRemoteService.class.getName());
        return rmic;
    }

    public interface ExampleRemote extends Remote {
    }

    public static class ExampleRemoteService implements ExampleRemote {
    }
}
