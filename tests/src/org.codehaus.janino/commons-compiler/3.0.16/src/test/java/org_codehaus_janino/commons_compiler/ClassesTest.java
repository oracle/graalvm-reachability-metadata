/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import java.lang.reflect.Method;

import org.codehaus.commons.compiler.util.reflect.Classes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassesTest {
    @Test
    void loadsClassThroughProvidedClassLoader() {
        Class<?> loadedClass = Classes.load(Thread.currentThread().getContextClassLoader(), String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void findsDeclaredMethodOnClass() {
        Method substring = Classes.getDeclaredMethod(String.class, "substring", int.class, int.class);

        assertThat(substring.getDeclaringClass()).isSameAs(String.class);
        assertThat(substring.getName()).isEqualTo("substring");
        assertThat(substring.getParameterTypes()).containsExactly(int.class, int.class);
        assertThat(substring.getReturnType()).isSameAs(String.class);
    }
}
