/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_common.hibernate_commons_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XMethod;
import org.hibernate.annotations.common.reflection.java.JavaReflectionManager;
import org.junit.jupiter.api.Test;

public class JavaXMethodTest {
    @Test
    public void invokesDeclaredMethodsWithAndWithoutArguments() {
        JavaReflectionManager reflectionManager = new JavaReflectionManager();
        XClass targetClass = reflectionManager.toXClass(InvokedMethods.class);
        Map<String, XMethod> methods = methodsByName(targetClass.getDeclaredMethods());
        InvokedMethods target = new InvokedMethods("hibernate");

        assertThat(methods.get("describe").invoke(target)).isEqualTo("hibernate annotations");
        assertThat(methods.get("join").invoke(target, "commons", 2)).isEqualTo("hibernate-commons-2");
    }

    private static Map<String, XMethod> methodsByName(List<XMethod> methods) {
        Map<String, XMethod> result = new HashMap<>();
        for (XMethod method : methods) {
            result.put(method.getName(), method);
        }
        return result;
    }

    public static class InvokedMethods {
        private final String prefix;

        public InvokedMethods(String prefix) {
            this.prefix = prefix;
        }

        public String describe() {
            return prefix + " annotations";
        }

        public String join(String value, int count) {
            return prefix + "-" + value + "-" + count;
        }
    }
}
