/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Typedef;
import org.junit.jupiter.api.Test;

public class DefinerTest {
    @Test
    void definesAndAdaptsPojoTaskByClassName() {
        Project project = new Project();
        Typedef definition = new Typedef();
        definition.setProject(project);
        definition.init();
        definition.setName("record");
        definition.setClassname(TaskAdapterTest.PropertyTask.class.getName());
        definition.setAdapter("org.apache.tools.ant.TaskAdapter");
        definition.setAdaptTo("org.apache.tools.ant.Task");
        definition.execute();
        ((Task) project.createDataType("record")).execute();
        assertEquals("executed", project.getProperty("adapter.result"));
    }
}
