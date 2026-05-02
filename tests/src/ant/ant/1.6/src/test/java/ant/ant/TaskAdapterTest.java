/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.TaskAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class TaskAdapterTest {
    @Test
    void validatesAndExecutesBeanTaskThroughAdapter() {
        Project project = new Project();
        AdaptedBeanTask proxy = new AdaptedBeanTask();
        TaskAdapter adapter = new TaskAdapter();
        adapter.setProject(project);
        adapter.setProxy(proxy);

        assertThatCode(() -> TaskAdapter.checkTaskClass(AdaptedBeanTask.class, project))
                .doesNotThrowAnyException();
        assertThatCode(() -> adapter.checkProxyClass(AdaptedBeanTask.class))
                .doesNotThrowAnyException();

        adapter.execute();

        assertThat(adapter.getProxy()).isSameAs(proxy);
        assertThat(proxy.project).isSameAs(project);
        assertThat(proxy.executed).isTrue();
    }

    public static class AdaptedBeanTask {
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
