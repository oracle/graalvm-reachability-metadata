/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PatchFixesHiderInnerUtilTest {
    private static final String UTIL_CLASS_NAME = "lombok.launch.PatchFixesHider$Util";

    @Test
    void shadowLoadClassLoadsAClassThroughLomboksSelectedLoader() throws Exception {
        Class<?> loadedClass = (Class<?>) invokeUtil(
                "shadowLoadClass",
                new Class<?>[] {String.class},
                "java.lang.String"
        );

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void findMethodFindsMethodByConcreteParameterTypes() throws Exception {
        Method method = (Method) invokeUtil(
                "findMethod",
                new Class<?>[] {Class.class, String.class, Class[].class},
                PatchFixesHiderInnerUtilTest.class,
                "formatForLombokUtil",
                new Class<?>[] {String.class, Integer.class}
        );

        assertThat(method.getName()).isEqualTo("formatForLombokUtil");
        assertThat(method.getParameterTypes()).containsExactly(String.class, Integer.class);
    }

    @Test
    void findMethodFindsMethodByParameterTypeNames() throws Exception {
        Method method = (Method) invokeUtil(
                "findMethod",
                new Class<?>[] {Class.class, String.class, String[].class},
                PatchFixesHiderInnerUtilTest.class,
                "formatForLombokUtil",
                new String[] {"java.lang.String", "java.lang.Integer"}
        );

        assertThat(method.getName()).isEqualTo("formatForLombokUtil");
        assertThat(method.getParameterTypes()).containsExactly(String.class, Integer.class);
    }

    @Test
    void findMethodAnyArgsFindsMethodWithoutSpecifyingParameters() throws Exception {
        Method method = (Method) invokeUtil(
                "findMethodAnyArgs",
                new Class<?>[] {Class.class, String.class},
                PatchFixesHiderInnerUtilTest.class,
                "methodWithAnyArgsForLombokUtil"
        );

        assertThat(method.getName()).isEqualTo("methodWithAnyArgsForLombokUtil");
    }

    @Test
    void invokeMethodInvokesStaticMethodWithArguments() throws Exception {
        Method method = (Method) invokeUtil(
                "findMethod",
                new Class<?>[] {Class.class, String.class, Class[].class},
                PatchFixesHiderInnerUtilTest.class,
                "formatForLombokUtil",
                new Class<?>[] {String.class, Integer.class}
        );

        Object result = invokeUtil(
                "invokeMethod",
                new Class<?>[] {Method.class, Object[].class},
                method,
                new Object[] {"value", 7}
        );

        assertThat(result).isEqualTo("value:7");
    }

    public static String formatForLombokUtil(String value, Integer number) {
        return value + ":" + number;
    }

    public static String methodWithAnyArgsForLombokUtil(String value) {
        return value;
    }

    private static Object invokeUtil(String methodName, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Class<?> utilClass = Class.forName(UTIL_CLASS_NAME);
        Method method = utilClass.getMethod(methodName, parameterTypes);
        return method.invoke(null, arguments);
    }
}
