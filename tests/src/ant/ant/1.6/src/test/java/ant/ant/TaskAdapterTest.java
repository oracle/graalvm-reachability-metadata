/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.TaskAdapter;
import org.junit.jupiter.api.Test;

public class TaskAdapterTest {

    @Test
    void adaptsAndExecutesAPlainTaskObject() {
        Project project = new Project();
        AntTypeDefinition definition = new AntTypeDefinition();
        definition.setName("adaptedTask");
        definition.setClass(AdaptedTask.class);
        definition.setAdapterClass(TaskAdapter.class);
        definition.setAdaptToClass(Task.class);

        definition.checkClass(project);
        TaskAdapter adapter = (TaskAdapter) definition.create(project);
        adapter.execute();

        AdaptedTask adaptedTask = (AdaptedTask) adapter.getProxy();
        assertThat(adaptedTask.project).isSameAs(project);
        assertThat(adaptedTask.executed).isTrue();
    }

    public static final class AdaptedTask {
        private Project project;
        private boolean executed;

        public void setProject(Project project) {
            this.project = project;
        }

        public void execute() {
            executed = true;
        }
    }
}
