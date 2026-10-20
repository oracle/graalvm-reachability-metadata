/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.pulsar.shade.org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {
    @Test
    void resolvesClassNamesArraysAndPublicInterfaceMethods() throws Exception {
        List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(
                List.of("java.lang.String", "java.util.ArrayList", "missing.Type"));
        Class<?> arrayClass = ClassUtils.getClass(getClass().getClassLoader(), "java.lang.String[]");
        Method directMethod = ClassUtils.getPublicMethod(String.class, "length");
        Method interfaceMethod = ClassUtils.getPublicMethod(HiddenGreeter.class, "greet", String.class);

        assertThat(classes).containsExactly(String.class, java.util.ArrayList.class, null);
        assertThat(arrayClass).isEqualTo(String[].class);
        assertThat(directMethod.getDeclaringClass()).isEqualTo(String.class);
        assertThat(interfaceMethod.getDeclaringClass()).isEqualTo(Greeter.class);
        assertThat(new HiddenGreeter().greet("client")).isEqualTo("hello client");
    }

    public interface Greeter {
        String greet(String name);
    }

    private static class HiddenGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }
    }
}
