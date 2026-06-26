/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import java.lang.reflect.Method;

import org.codehaus.plexus.util.introspection.ClassMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassMapInnerMethodInfoTest {
    @Test
    void upcastsMethodsFromNonPublicImplementationToPublicInterface() throws Exception {
        ClassMap classMap = new ClassMap(NonPublicGreeter.class);

        Method method = classMap.findMethod("greet", new Object[] {"Ada"});

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(PublicGreeting.class);
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    public interface PublicGreeting {
        String greet(String name);
    }

    private static class NonPublicGreeter implements PublicGreeting {
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
