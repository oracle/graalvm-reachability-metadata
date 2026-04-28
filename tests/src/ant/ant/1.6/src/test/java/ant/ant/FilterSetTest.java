/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Hashtable;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FilterSet;
import org.apache.tools.ant.types.Reference;
import org.junit.jupiter.api.Test;

public class FilterSetTest {
    @Test
    void resolvesReferencedFilterSetForTokenReplacement() {
        Project project = new Project();
        project.init();

        FilterSet source = new FilterSet();
        source.setProject(project);
        source.setBeginToken("${");
        source.setEndToken("}");
        source.addFilter("library", "Ant");
        source.addFilter("message", "${library} filter set");
        project.addReference("shared.filters", source);

        FilterSet referenced = new FilterSet();
        referenced.setProject(project);
        referenced.setRefid(new Reference("shared.filters"));

        String replaced = referenced.replaceTokens("Testing ${message}");
        Hashtable filterHash = referenced.getFilterHash();

        assertThat(replaced).isEqualTo("Testing Ant filter set");
        assertThat(referenced.getBeginToken()).isEqualTo("${");
        assertThat(referenced.getEndToken()).isEqualTo("}");
        assertThat(filterHash).containsEntry("library", "Ant");
        assertThat(referenced.hasFilters()).isTrue();
    }
}
