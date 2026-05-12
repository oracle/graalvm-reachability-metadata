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

public class HiddenClassMapTest {
    @Test
    void buildsMethodCacheFromAccessibleMethods() throws Exception {
        ClassMap classMap = new ClassMap(HiddenTask.class);

        Method method = classMap.findMethod("describe", new Object[] {"compile"});

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(Task.class);
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void resolvesNonPublicImplementationMethodToPublicInterfaceMethod() throws Exception {
        Method implementationMethod = HiddenTask.class.getDeclaredMethod("describe", String.class);

        Method publicMethod = ClassMap.getPublicMethod(implementationMethod);

        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getDeclaringClass()).isEqualTo(Task.class);
        assertThat(publicMethod.getParameterTypes()).containsExactly(String.class);
    }

    public interface Task {
        String describe(String name);
    }

    private static final class HiddenTask implements Task {
        public String describe(String name) {
            return "task " + name;
        }
    }
}
