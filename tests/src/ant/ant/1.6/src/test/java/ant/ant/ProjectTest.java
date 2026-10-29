/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;
import org.junit.jupiter.api.Test;

public class ProjectTest {
    @Test
    void registersAndExecutesPojoTaskAndCreatesDelegatingLoader() throws Exception {
        Project project = new Project();
        project.checkTaskClass(TaskAdapterTest.PropertyTask.class);
        project.addTaskDefinition("record", TaskAdapterTest.PropertyTask.class);
        project.createTask("record").execute();
        assertEquals("executed", project.getProperty("adapter.result"));
        TaskAdapterTest.PropertyTask pojo = new TaskAdapterTest.PropertyTask();
        project.setProjectReference(pojo);
        pojo.execute();
        AntClassLoader loader = project.createClassLoader(new Path(project));
        try {
            assertSame(String.class, loader.loadClass("java.lang.String"));
        } finally {
            loader.cleanup();
        }
    }
}
