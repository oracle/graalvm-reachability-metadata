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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RmicTest {
    @Test
    void findsRemoteInterfaceImplementedByClass() {
        Rmic rmic = newRmic();

        assertThat(rmic.getRemoteInterface(ExampleRemoteService.class))
                .isEqualTo(ExampleRemote.class);
    }

    @Test
    void validatesRemoteImplementationByClassName(@TempDir Path baseDirectory) {
        Rmic rmic = newRmic();
        rmic.setBase(baseDirectory.toFile());
        rmic.execute();

        assertThat(rmic.isValidRmiRemote(ExampleRemoteService.class.getName()))
                .isTrue();
    }

    private static Rmic newRmic() {
        Project project = new Project();
        project.init();

        Rmic rmic = new Rmic();
        rmic.setProject(project);
        rmic.setTaskName("rmic");
        return rmic;
    }

    public interface ExampleRemote extends Remote {
    }

    public static class ExampleRemoteService implements ExampleRemote {
    }
}
