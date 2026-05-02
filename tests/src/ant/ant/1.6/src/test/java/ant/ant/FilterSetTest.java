/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FilterSet;
import org.apache.tools.ant.types.Reference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FilterSetTest {
    @Test
    void resolvesReferencedFilterSetWhenReplacingTokens() {
        Project project = new Project();
        FilterSet source = new FilterSet();
        source.setProject(project);
        source.addFilter("name", "Ant");
        source.addFilter("greeting", "Hello @name@");
        project.addReference("sharedFilters", source);

        FilterSet reference = new FilterSet();
        reference.setProject(project);
        reference.setRefid(new Reference("sharedFilters"));

        assertThat(reference.hasFilters()).isTrue();
        assertThat(reference.getFilterHash()).containsEntry("name", "Ant");
        assertThat(reference.replaceTokens("@greeting@ users"))
                .isEqualTo("Hello Ant users");
    }
}
