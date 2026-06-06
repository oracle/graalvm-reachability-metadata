/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_common.hibernate_commons_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XMethod;
import org.hibernate.annotations.common.reflection.java.JavaReflectionManager;
import org.junit.jupiter.api.Test;

public class JavaXClassTest {
    @Test
    public void getDeclaredMethodsWrapsEveryDeclaredMethod() {
        JavaReflectionManager reflectionManager = new JavaReflectionManager();
        XClass xClass = reflectionManager.toXClass(MethodFixture.class);

        List<XMethod> methods = xClass.getDeclaredMethods();

        assertThat(methods)
                .extracting(XMethod::getName)
                .contains("describe", "increment", "hiddenValue");
        assertThat(method(methods, "describe").invoke(new MethodFixture(), "hibernate"))
                .isEqualTo("MethodFixture:hibernate");
        assertThat(reflectionManager.toClass(method(methods, "increment").getType())).isEqualTo(int.class);
    }

    private static XMethod method(List<XMethod> methods, String name) {
        return methods.stream()
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + name));
    }

    public static class MethodFixture {
        public String describe(String value) {
            return "MethodFixture:" + value;
        }

        public int increment(int value) {
            return value + 1;
        }

        private String hiddenValue() {
            return "hidden";
        }
    }
}
