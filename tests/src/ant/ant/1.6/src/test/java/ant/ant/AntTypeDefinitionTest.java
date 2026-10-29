/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FilterSet;
import org.apache.tools.ant.types.Path;
import org.junit.jupiter.api.Test;

public class AntTypeDefinitionTest {
    @Test
    void constructsNoArgAndProjectAwareTypesByName() {
        Project project = new Project();
        AntTypeDefinition plain = new AntTypeDefinition();
        plain.setName("filters");
        plain.setClassName("org.apache.tools.ant.types.FilterSet");
        FilterSet filters = (FilterSet) plain.create(project);
        filters.addFilter("kind", "configured");
        assertEquals("configured", filters.replaceTokens("@kind@"));

        AntTypeDefinition pathType = new AntTypeDefinition();
        pathType.setName("path");
        pathType.setClassName("org.apache.tools.ant.types.Path");
        pathType.setClassLoader(getClass().getClassLoader());
        Path path = (Path) pathType.create(project);
        assertSame(project, path.getProject());
        path.setLocation(project.resolveFile("input"));
        assertEquals(project.resolveFile("input").getAbsolutePath(), path.list()[0]);
    }
}
