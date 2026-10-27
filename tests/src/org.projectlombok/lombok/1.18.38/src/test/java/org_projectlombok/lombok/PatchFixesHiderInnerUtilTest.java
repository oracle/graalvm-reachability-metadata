/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class PatchFixesHiderInnerUtilTest {
    @Test
    void utilityMethodsLoadFindAndInvokeStaticMethodsAcrossTheShadowLoader() throws Exception {
        Class<?> utilityClass = Class.forName("lombok.launch.PatchFixesHider$Util");

        Method shadowLoadClass = utilityClass.getMethod("shadowLoadClass", String.class);
        Class<?> stringClass = (Class<?>) shadowLoadClass.invoke(null, "java.lang.String");
        assertThat(stringClass).isSameAs(String.class);

        Method findMethodByClasses = utilityClass.getMethod(
                "findMethod", Class.class, String.class, Class[].class);
        Method valueOfInt = (Method) findMethodByClasses.invoke(
                null, String.class, "valueOf", new Class<?>[] {int.class});
        assertThat(valueOfInt.getDeclaringClass()).isSameAs(String.class);

        Method findMethodByNames = utilityClass.getMethod(
                "findMethod", Class.class, String.class, String[].class);
        Method maxInts = (Method) findMethodByNames.invoke(
                null, Math.class, "max", new String[] {"int", "int"});
        assertThat(maxInts.getDeclaringClass()).isSameAs(Math.class);

        Method findMethodAnyArgs = utilityClass.getMethod("findMethodAnyArgs", Class.class, String.class);
        Method abs = (Method) findMethodAnyArgs.invoke(null, Math.class, "abs");
        assertThat(abs.getDeclaringClass()).isSameAs(Math.class);

        Method invokeMethod = utilityClass.getMethod("invokeMethod", Method.class, Object[].class);
        Object value = invokeMethod.invoke(null, valueOfInt, new Object[] {123});
        assertThat(value).isEqualTo("123");
    }
}
