/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.rmic.KaffeRmic;
import org.apache.tools.ant.taskdefs.rmic.RmicAdapter;
import org.apache.tools.ant.taskdefs.rmic.RmicAdapterFactory;
import org.junit.jupiter.api.Test;

public class RmicAdapterFactoryTest {
    @Test
    void createsRmicAdapterFromFullyQualifiedAdapterClassName() {
        RmicAdapter adapter = RmicAdapterFactory.getRmic(KaffeRmic.class.getName(), newTask());

        assertThat(adapter).isInstanceOf(KaffeRmic.class);
    }

    private static Task newTask() {
        Project project = new Project();
        project.init();

        Task task = new LoggingTask();
        task.setProject(project);
        task.setTaskName("rmic");
        return task;
    }

    private static final class LoggingTask extends Task {
    }
}
