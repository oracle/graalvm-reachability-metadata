/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Property;
import org.junit.jupiter.api.Test;

public class IntrospectionHelperAnonymous5Test {
    @Test
    void setsStringAttribute() {
        Project project = new Project();
        Property property = new Property();
        property.setProject(project);
        property.setValue("release");
        IntrospectionHelper.getHelper(Property.class).setAttribute(project, property, "name", "build.kind");
        property.execute();
        assertEquals("release", project.getProperty("build.kind"));
    }
}
