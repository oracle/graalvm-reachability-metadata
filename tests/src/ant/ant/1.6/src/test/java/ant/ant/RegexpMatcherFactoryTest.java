/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.util.regexp.Jdk14RegexpMatcher;
import org.apache.tools.ant.util.regexp.RegexpMatcher;
import org.apache.tools.ant.util.regexp.RegexpMatcherFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RegexpMatcherFactoryTest {
    @Test
    void createsDefaultJdkRegexpMatcherWhenNoProjectOverrideIsConfigured() {
        Project project = new Project();
        project.init();

        RegexpMatcher matcher = new RegexpMatcherFactory().newRegexpMatcher(project);
        matcher.setPattern("build-(\\d+)");

        assertThat(matcher).isInstanceOf(Jdk14RegexpMatcher.class);
        assertThat(matcher.matches("native build-42 completed")).isTrue();
        assertThat(matcher.getGroups("native build-42 completed")).containsExactly("build-42", "42");
    }
}
