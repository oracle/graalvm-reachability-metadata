/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.codehaus.commons.compiler.util.reflect.Classes;
import org.junit.jupiter.api.Test;

public class ClassesTest {

    @Test
    void loadsClassesAndDeclaredMethods() {
        Class<?> loadedClass = Classes.load(String.class.getName());
        Method declaredMethod = Classes.getDeclaredMethod(DeclaredMethodTarget.class, "hidden", String.class);

        assertThat(loadedClass).isEqualTo(String.class);
        assertThat(declaredMethod.getName()).isEqualTo("hidden");
    }

    public static final class DeclaredMethodTarget {
        private static String hidden(String value) {
            return value.toUpperCase();
        }
    }
}
