/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.velocity.util.introspection.ClassMap;
import org.junit.jupiter.api.Test;

public class ClassMapInnerMethodInfoTest {
    @Test
    void upcastsNonPublicImplementationMethodToPublicInterfaceMethod() throws Exception {
        final ClassMap classMap = new ClassMap(HiddenGreetingService.class);

        final Method method = classMap.findMethod("greet", new Object[] {"Velocity"});

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isSameAs(GreetingService.class);
        assertThat(method.getName()).isEqualTo("greet");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    public interface GreetingService {
        String greet(String name);
    }

    private static final class HiddenGreetingService implements GreetingService {
        @Override
        public String greet(final String name) {
            return "Hello " + name;
        }
    }
}
