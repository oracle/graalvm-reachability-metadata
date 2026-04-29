/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_common.hibernate_commons_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XMethod;
import org.hibernate.annotations.common.reflection.java.JavaReflectionManager;
import org.junit.jupiter.api.Test;

public class JavaXClassTest {
    @Test
    public void exposesAllDeclaredMethodsThroughTheReflectionManager() {
        JavaReflectionManager reflectionManager = new JavaReflectionManager();
        XClass targetClass = reflectionManager.toXClass(DeclaredMethods.class);
        List<XMethod> declaredMethods = targetClass.getDeclaredMethods();

        assertThat(methodNames(declaredMethods))
                .contains("describe", "hiddenStatus", "methodWithParameters")
                .doesNotContain("toString");
    }

    private static List<String> methodNames(List<XMethod> declaredMethods) {
        List<String> names = new ArrayList<>();
        for (XMethod method : declaredMethods) {
            names.add(method.getName());
        }
        return names;
    }

    public static class DeclaredMethods {
        public String describe() {
            return "declared method";
        }

        private boolean hiddenStatus() {
            return true;
        }

        public int methodWithParameters(String value, int count) {
            return value.length() + count;
        }
    }
}
