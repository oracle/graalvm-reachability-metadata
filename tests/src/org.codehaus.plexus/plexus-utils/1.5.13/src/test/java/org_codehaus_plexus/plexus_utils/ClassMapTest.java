/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import java.lang.reflect.Method;

import org.codehaus.plexus.util.introspection.ClassMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassMapTest {
    @Test
    void buildsCacheFromPublicMethodsAndFindsMatchingMethod() throws Exception {
        ClassMap classMap = new ClassMap(PublicGreeter.class);

        Method method = classMap.findMethod("greet", new Object[] {"Ada"});

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("greet");
        assertThat(method.getDeclaringClass()).isEqualTo(PublicGreeter.class);
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void resolvesNonPublicImplementationMethodToPublicInterfaceMethod() throws Exception {
        Method implementationMethod = HiddenGreeter.class.getDeclaredMethod("greet", String.class);

        Method publicMethod = ClassMap.getPublicMethod(implementationMethod);

        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getDeclaringClass()).isEqualTo(Greeting.class);
        assertThat(publicMethod.getParameterTypes()).containsExactly(String.class);
    }

    public interface Greeting {
        String greet(String name);
    }

    public static class PublicGreeter {
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    private static class HiddenGreeter implements Greeting {
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
