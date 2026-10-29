/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Available;
import org.junit.jupiter.api.Test;

public class AvailableTest {
    @Test
    void checksClassAndResourceAndSetsProperty() {
        Project project = new Project();
        Available available = new Available();
        available.setProject(project);
        available.setClassname("org.apache.tools.ant.taskdefs.Echo");
        available.setResource("org/apache/tools/ant/taskdefs/defaults.properties");
        available.setProperty("ant.available");
        available.setValue("present");
        assertTrue(available.eval());
        available.execute();
        assertEquals("present", project.getProperty("ant.available"));
    }
}
