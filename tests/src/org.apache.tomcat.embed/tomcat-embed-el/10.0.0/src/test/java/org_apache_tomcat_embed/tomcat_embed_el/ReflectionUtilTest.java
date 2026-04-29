/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import java.lang.reflect.Method;

import javax.el.MethodNotFoundException;

import org.apache.el.util.ReflectionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilTest {

    @Test
    void resolvesRegularAndArrayClassNames() throws ClassNotFoundException {
        assertThat(ReflectionUtil.forName("java.lang.String")).isSameAs(String.class);
        assertThat(ReflectionUtil.forName("java.lang.String[]")).isSameAs(String[].class);
    }

    @Test
    void resolvesInterfaceMethodForNonPublicBaseClass() throws MethodNotFoundException {
        Method method = ReflectionUtil.getMethod(
                null,
                new InterfaceBackedTarget(),
                "echo",
                new Class<?>[]{String.class},
                new Object[]{"value"});

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(PublicEchoContract.class);
        assertThat(method.getName()).isEqualTo("echo");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void resolvesSuperclassMethodForNonPublicBaseClass() throws MethodNotFoundException {
        Method method = ReflectionUtil.getMethod(
                null,
                new SuperclassBackedTarget(),
                "echo",
                new Class<?>[]{String.class},
                new Object[]{"value"});

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(PublicEchoBase.class);
        assertThat(method.getName()).isEqualTo("echo");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    public interface PublicEchoContract {
        String echo(String value);
    }

    public static class PublicEchoBase {
        public String echo(String value) {
            return value;
        }
    }

    private static final class InterfaceBackedTarget implements PublicEchoContract {
        @Override
        public String echo(String value) {
            return value;
        }
    }

    private static final class SuperclassBackedTarget extends PublicEchoBase {
    }
}
