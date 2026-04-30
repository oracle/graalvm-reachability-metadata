/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import org.codehaus.commons.compiler.util.reflect.Classes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassesTest {
    @Test
    void loadsClassWithExplicitClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        Class<?> loadedClass = Classes.load(classLoader, ReflectiveTarget.class.getName());

        assertThat(loadedClass).isSameAs(ReflectiveTarget.class);
    }

    @Test
    void findsDeclaredMethodByNameAndParameterTypes() {
        Method method = Classes.getDeclaredMethod(ReflectiveTarget.class, "formatMessage", String.class, int.class);

        assertThat(method.getName()).isEqualTo("formatMessage");
        assertThat(method.getReturnType()).isSameAs(String.class);
        assertThat(method.getParameterTypes()).containsExactly(String.class, int.class);
    }

    public static final class ReflectiveTarget {
        public String formatMessage(String prefix, int value) {
            return prefix + value;
        }
    }
}
