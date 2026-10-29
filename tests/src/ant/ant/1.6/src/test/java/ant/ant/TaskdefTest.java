/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Taskdef;
import org.junit.jupiter.api.Test;

public class TaskdefTest {
    @Test
    void registersAndExecutesAdaptedTask() {
        Project project = new Project();
        Taskdef taskdef = new Taskdef();
        taskdef.setProject(project);
        taskdef.init();
        taskdef.setName("record");
        taskdef.setClassname(TaskAdapterTest.PropertyTask.class.getName());
        taskdef.execute();
        project.createTask("record").execute();
        assertEquals("executed", project.getProperty("adapter.result"));
    }
}
