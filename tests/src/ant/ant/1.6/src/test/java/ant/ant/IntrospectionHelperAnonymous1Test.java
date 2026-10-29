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
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;
import org.junit.jupiter.api.Test;

public class IntrospectionHelperAnonymous1Test {
    @Test
    void createsNestedElementUsingFactoryMethod() {
        Project project = new Project();
        Path path = new Path(project);
        IntrospectionHelper helper = IntrospectionHelper.getHelper(Path.class);
        PathElement child = (PathElement) helper.createElement(project, path, "pathelement");
        child.setLocation(project.resolveFile("input"));
        assertEquals(project.resolveFile("input").getAbsolutePath(), path.list()[0]);
    }
}
