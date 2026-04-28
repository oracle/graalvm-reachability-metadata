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
import org.apache.tools.ant.taskdefs.Taskdef;
import org.junit.jupiter.api.Test;

public class TaskdefTest {
    @Test
    void registersTaskDefinitionWithDefaultTaskContract() {
        Project project = new Project();
        project.init();
        Taskdef taskdef = new Taskdef();
        taskdef.setProject(project);
        taskdef.setTaskName("taskdef");
        taskdef.init();
        taskdef.setName("recording-task");
        taskdef.setClassname(RecordingTask.class.getName());

        taskdef.execute();

        Task task = project.createTask("recording-task");
        assertThat(task).isInstanceOf(RecordingTask.class);
        assertThat(task.getProject()).isSameAs(project);

        task.execute();

        assertThat(project.getProperty("taskdef.recording-task.executed")).isEqualTo("true");
    }

    public static final class RecordingTask extends Task {
        public RecordingTask() {
        }

        @Override
        public void execute() {
            getProject().setProperty("taskdef.recording-task.executed", "true");
        }
    }
}
