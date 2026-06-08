/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.velocity.util.introspection.ClassMap;
import org.junit.jupiter.api.Test;

public class ClassMapInnerMethodInfoTest {
    @Test
    void upcastsNonPublicImplementationMethodToPublicInterfaceMethod() throws Exception {
        final ClassMap classMap = new ClassMap(HiddenLabeler.class);

        final Method method = classMap.findMethod("label", new Object[] {Integer.valueOf(7)});

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(Labeler.class);
        assertThat(method.getName()).isEqualTo("label");
    }

    public interface Labeler {
        String label(Integer value);
    }

    private static final class HiddenLabeler implements Labeler {
        @Override
        public String label(final Integer value) {
            return "value=" + value;
        }
    }
}
