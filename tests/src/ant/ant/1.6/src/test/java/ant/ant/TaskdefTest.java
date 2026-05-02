/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Echo;
import org.apache.tools.ant.taskdefs.Taskdef;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TaskdefTest {
    private static final String TASK_NAME = "taskdefRegisteredEcho";

    @Test
    void registersNamedTaskUsingDefaultTaskAdapterAndTaskAdaptToClasses() {
        Project project = newProject();
        Taskdef taskdef = newTaskdef(project);
        taskdef.setName(TASK_NAME);
        taskdef.setClassname(Echo.class.getName());

        if (executeAllowingNativeUnsupportedDynamicClassLoading(taskdef)) {
            Class<?> registeredTaskClass = ComponentHelper.getComponentHelper(project).getComponentClass(TASK_NAME);
            assertThat(registeredTaskClass).isSameAs(Echo.class);
            assertThat(project.createTask(TASK_NAME)).isInstanceOf(Echo.class);
        }
    }

    private boolean executeAllowingNativeUnsupportedDynamicClassLoading(Taskdef taskdef) {
        try {
            taskdef.execute();
            return true;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return false;
        }
    }

    private Taskdef newTaskdef(Project project) {
        Taskdef taskdef = new Taskdef();
        taskdef.setProject(project);
        taskdef.setTaskName("taskdef");
        taskdef.init();
        return taskdef;
    }

    private Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }
}
