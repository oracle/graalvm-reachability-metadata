/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.nio.file.Path;

import kaffe.rmi.rmic.RMIC;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Rmic;
import org.apache.tools.ant.taskdefs.rmic.KaffeRmic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class KaffeRmicTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void executeLoadsKaffeRmicImplementationAndRunsIt() {
        assertThat(RMIC.class.getName()).isEqualTo("kaffe.rmi.rmic.RMIC");

        Rmic rmic = newRmic();
        KaffeRmic adapter = new KaffeRmic();
        adapter.setRmic(rmic);

        boolean completed = adapter.execute();

        assertThat(completed).isTrue();
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
