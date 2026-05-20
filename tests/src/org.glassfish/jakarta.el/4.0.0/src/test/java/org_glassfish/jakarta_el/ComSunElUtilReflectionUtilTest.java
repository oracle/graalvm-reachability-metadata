/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.jakarta_el;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.sun.el.util.ReflectionUtil;

import jakarta.el.ELContext;
import jakarta.el.ELProcessor;

public class ComSunElUtilReflectionUtilTest {

    @Test
    void resolvesRegularAndArrayTypesByName() throws Exception {
        Class<?> stringType = ReflectionUtil.forName("java.lang.String");
        Class<?> stringArrayType = ReflectionUtil.forName("java.lang.String[]");

        assertThat(stringType).isSameAs(String.class);
        assertThat(stringArrayType).isSameAs(String[].class);
    }

    @Test
    void invokesVarArgMethodWithConvertedParameters() {
        ELContext context = new ELProcessor().getELManager().getELContext();
        Method method = ReflectionUtil.findMethod(
                VarArgFixture.class,
                "join",
                new Class<?>[] { String.class, String.class, String.class },
                new Object[] { "prefix", "one", "two" });

        Object result = ReflectionUtil.invokeMethod(
                context,
                method,
                new VarArgFixture(),
                new Object[] { "prefix", "one", "two" });

        assertThat(result).isEqualTo("prefix:one,two");
    }

    @Test
    void resolvesMethodFromPublicInterfaceForNonPublicImplementation() {
        Method method = ReflectionUtil.findMethod(
                InterfaceImplementation.class,
                "label",
                new Class<?>[] { String.class },
                new Object[] { "value" });

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isSameAs(LabelProvider.class);
    }

    @Test
    void resolvesMethodFromPublicSuperclassForNonPublicSubclass() {
        Method method = ReflectionUtil.findMethod(
                SuperclassImplementation.class,
                "format",
                new Class<?>[] { String.class },
                new Object[] { "value" });

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isSameAs(PublicSuperclass.class);
    }

    public static final class VarArgFixture {
        public String join(String prefix, String... values) {
            return prefix + ":" + String.join(",", values);
        }
    }

    public interface LabelProvider {
        String label(String value);
    }

    private static final class InterfaceImplementation implements LabelProvider {
        @Override
        public String label(String value) {
            return "label:" + value;
        }
    }

    public static class PublicSuperclass {
        public String format(String value) {
            return "format:" + value;
        }
    }

    private static final class SuperclassImplementation extends PublicSuperclass {
    }
}
