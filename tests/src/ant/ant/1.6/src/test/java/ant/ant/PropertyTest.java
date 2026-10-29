/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Property;
import org.junit.jupiter.api.Test;

public class PropertyTest {
    @Test
    void loadsAndExpandsClasspathProperties() {
        Project project = new Project();
        project.setProperty("user", "Ant");
        Property property = new Property();
        property.setProject(project);
        property.setResource("ant-test.properties");
        property.execute();
        assertEquals("Hello Ant", project.getProperty("greeting"));
        assertEquals("ready", project.getProperty("state"));
    }
}
