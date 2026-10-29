/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.TaskAdapter;
import org.junit.jupiter.api.Test;

public class TaskAdapterTest {
    @Test
    void executesProjectAwarePojoTask() {
        Project project = new Project();
        PropertyTask task = new PropertyTask();
        TaskAdapter adapter = new TaskAdapter();
        adapter.setProject(project);
        adapter.checkProxyClass(PropertyTask.class);
        adapter.setProxy(task);
        adapter.execute();
        assertSame(project, task.project);
        assertEquals("executed", project.getProperty("adapter.result"));
    }

    public static class PropertyTask {
        private Project project;

        public void setProject(Project project) {
            this.project = project;
        }

        public void execute() {
            project.setNewProperty("adapter.result", "executed");
        }
    }
}
