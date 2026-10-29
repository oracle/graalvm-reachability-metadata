/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FilterSet;
import org.apache.tools.ant.types.Reference;
import org.junit.jupiter.api.Test;

public class FilterSetTest {
    @Test
    void expandsTokensThroughFilterSetReference() {
        Project project = new Project();
        FilterSet original = new FilterSet();
        original.setProject(project);
        original.addFilter("name", "Ant");
        project.addReference("filters", original);
        FilterSet reference = new FilterSet();
        reference.setProject(project);
        reference.setRefid(new Reference("filters"));
        assertEquals("Hello Ant", reference.replaceTokens("Hello @name@"));
    }
}
