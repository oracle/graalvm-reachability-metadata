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

public class PatchFixesHiderUtilDynamicAccessTest {
    @Test
    void patchFixesUtilLoadsShadowClassesAndInvokesMethods() throws Throwable {
        Class<?> utilClass = LombokLaunchTestSupport.loadClass("lombok.launch.PatchFixesHider$Util");

        Class<?> shadowVersionClass = (Class<?>) LombokLaunchTestSupport.invoke(
                null,
                utilClass,
                "shadowLoadClass",
                new Class<?>[] {String.class},
                "lombok.core.Version");

        Method typedMethod = (Method) LombokLaunchTestSupport.invoke(
                null,
                utilClass,
                "findMethod",
                new Class<?>[] {Class.class, String.class, Class[].class},
                shadowVersionClass,
                "main",
                new Class<?>[] {String[].class});
        Method namedMethod = (Method) LombokLaunchTestSupport.invoke(
                null,
                utilClass,
                "findMethod",
                new Class<?>[] {Class.class, String.class, String[].class},
                shadowVersionClass,
                "main",
                new String[] {String[].class.getName()});
        Method anyArgsMethod = (Method) LombokLaunchTestSupport.invoke(
                null,
                utilClass,
                "findMethodAnyArgs",
                new Class<?>[] {Class.class, String.class},
                shadowVersionClass,
                "getVersion");
        String version = (String) LombokLaunchTestSupport.invoke(
                null,
                utilClass,
                "invokeMethod",
                new Class<?>[] {Method.class, Object[].class},
                anyArgsMethod,
                new Object[] {});
        assertThat(typedMethod.getName()).isEqualTo("main");
        assertThat(namedMethod.getName()).isEqualTo("main");
        assertThat(version).isEqualTo((String) LombokLaunchTestSupport.invoke(
                null,
                shadowVersionClass,
                "getVersion",
                new Class<?>[0]));
    }
}
