/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.util.introspection.ClassMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassMapInnerMethodInfoTest {
    @Test
    void upcastsMethodsFromNonPublicImplementationToPublicInterface() throws Exception {
        ClassMap classMap = new ClassMap(HiddenGreetingService.class);

        Method method = classMap.findMethod("greet", new Object[] {"Velocity"});

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(GreetingService.class);
        assertThat(method.getName()).isEqualTo("greet");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    public interface GreetingService {
        String greet(String name);
    }

    private static final class HiddenGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
