/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.util.ClasspathUtils;
import org.junit.jupiter.api.Test;

public class ClasspathUtilsTest {
    @Test
    void createsInstanceWithProvidedClassLoader() {
        ClassLoader loader = ClasspathUtilsTest.class.getClassLoader();

        Object instance = ClasspathUtils.newInstance(InstantiableComponent.class.getName(), loader);

        assertThat(instance).isInstanceOf(InstantiableComponent.class);
        assertThat(((InstantiableComponent) instance).message()).isEqualTo("created by ClasspathUtils");
    }

    public static final class InstantiableComponent {
        public InstantiableComponent() {
        }

        String message() {
            return "created by ClasspathUtils";
        }
    }
}
