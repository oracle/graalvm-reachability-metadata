/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_web.javax_el;

import java.lang.reflect.Method;

import com.sun.el.util.ReflectionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilTest {

    @Test
    void loadsNamedClassAndArrayClass() throws Exception {
        Class<?> stringType = ReflectionUtil.forName("java.lang.String");
        Class<?> stringArrayType = ReflectionUtil.forName("java.lang.String[]");

        assertThat(stringType).isEqualTo(String.class);
        assertThat(stringArrayType).isEqualTo(String[].class);
    }

    @Test
    void findsPublicMethodWithExplicitParameterTypes() {
        Class<?>[] parameterTypes = new Class<?>[] {int.class, int.class};

        Method method = ReflectionUtil.getMethod("reflection", "substring", parameterTypes);

        assertThat(method.getName()).isEqualTo("substring");
        assertThat(method.getParameterTypes()).containsExactly(int.class, int.class);
    }

    @Test
    void findsAndInvokesMatchingPublicMethod() {
        Method method = ReflectionUtil.findMethod("reflection", "substring", new Object[] {0, 7});
        Object result = ReflectionUtil.invokeMethod("reflection", "substring", new Object[] {0, 7});

        assertThat(method.getName()).isEqualTo("substring");
        assertThat(result).isEqualTo("reflect");
    }
}
