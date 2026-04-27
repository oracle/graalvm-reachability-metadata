/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Vector;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.util.regexp.RegexpMatcher;
import org.apache.tools.ant.util.regexp.RegexpMatcherFactory;
import org.junit.jupiter.api.Test;

public class RegexpMatcherFactoryTest {
    @Test
    void createsDefaultJdkMatcherAfterAvailabilityProbe() {
        RegexpMatcher matcher = new RegexpMatcherFactory().newRegexpMatcher(newProject());
        matcher.setPattern("release-([0-9]+)");

        Vector<?> groups = matcher.getGroups("tested release-42 metadata");

        assertThat(matcher.getClass().getName()).isEqualTo("org.apache.tools.ant.util.regexp.Jdk14RegexpMatcher");
        assertThat(matcher.matches("tested release-42 metadata")).isTrue();
        assertThat(groups).hasSize(2);
        assertThat(groups.elementAt(0)).isEqualTo("release-42");
        assertThat(groups.elementAt(1)).isEqualTo("42");
    }

    private static Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }
}
