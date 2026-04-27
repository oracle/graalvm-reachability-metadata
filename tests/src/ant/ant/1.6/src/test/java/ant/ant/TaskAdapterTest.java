/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.TaskAdapter;
import org.junit.jupiter.api.Test;

public class TaskAdapterTest {
    @Test
    void validatesAndExecutesProjectAwareProxyTask() {
        Project project = new Project();
        ProjectAwareProxyTask proxyTask = new ProjectAwareProxyTask();
        TaskAdapter adapter = new TaskAdapter();
        adapter.setProject(project);
        adapter.setProxy(proxyTask);

        TaskAdapter.checkTaskClass(ProjectAwareProxyTask.class, project);
        adapter.checkProxyClass(proxyTask.getClass());
        adapter.execute();

        assertThat(adapter.getProxy()).isSameAs(proxyTask);
        assertThat(proxyTask.project).isSameAs(project);
        assertThat(proxyTask.executions).isEqualTo(1);
    }

    public static final class ProjectAwareProxyTask {
        private Project project;
        private int executions;

        public void setProject(Project project) {
            this.project = project;
        }

        public void execute() {
            executions++;
        }
    }
}
