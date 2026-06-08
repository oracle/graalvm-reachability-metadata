/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.util.introspection.ClassMap;
import org.junit.jupiter.api.Test;

public class ClassMapTest {
    @Test
    void resolvesPublicInterfaceMethodForNonPublicImplementationMethod() throws Exception {
        final ClassMap classMap = new ClassMap(
                HiddenGreetingService.class, new Log(new NullLogChute()));

        final Method publicMethod = classMap.findMethod("greet", new Object[] {"Ada"});

        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getDeclaringClass()).isEqualTo(GreetingService.class);
        assertThat(publicMethod.invoke(new HiddenGreetingService(), "Ada")).isEqualTo("Hello Ada");
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
