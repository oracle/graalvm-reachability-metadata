/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.rmi.Remote;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Rmic;
import org.apache.tools.ant.taskdefs.rmic.WLRmic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WLRmicTest {
    @BeforeEach
    void resetWebLogicRmic() throws ReflectiveOperationException {
        weblogic.rmic.reset();
        resetLegacyClassLiteralCache();
    }

    @Test
    void executesWebLogicRmicAdapterWithMainMethod(@TempDir Path baseDirectory) {
        WLRmic adapter = new WLRmic();
        adapter.setRmic(newRmic(baseDirectory));

        boolean executed = adapter.execute();

        assertThat(executed).isTrue();
        assertThat(weblogic.rmic.getLastArguments())
                .containsSubsequence("-noexit", "-d", baseDirectory.toFile().getAbsolutePath())
                .contains("-classpath", ExampleRemoteService.class.getName());
    }

    private static void resetLegacyClassLiteralCache() throws ReflectiveOperationException {
        Field field = WLRmic.class.getDeclaredField("array$Ljava$lang$String");
        field.setAccessible(true);
        field.set(null, null);
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
