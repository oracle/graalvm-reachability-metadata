/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.util.ClasspathUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathUtilsTest {
    @Test
    void instantiatesAntClassWithProvidedLoader() {
        Object instance = ClasspathUtils.newInstance(
                Project.class.getName(),
                Project.class.getClassLoader());

        assertThat(instance).isInstanceOf(Project.class);
    }
}
