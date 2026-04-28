/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class PatchFixesHiderInnerUtilTest {

    @Test
    void loadsShadowClassesFindsMethodsAndInvokesStaticHelpers() throws Exception {
        Class<?> utilClass = Class.forName("lombok.launch.PatchFixesHider$Util");

        Method shadowLoadClass = utilClass.getDeclaredMethod("shadowLoadClass", String.class);
        shadowLoadClass.setAccessible(true);
        Class<?> loadedType = (Class<?>) shadowLoadClass.invoke(null, "lombok.core.Version");
        assertThat(loadedType.getName()).isEqualTo("lombok.core.Version");

        Method findMethodWithTypes = utilClass.getDeclaredMethod("findMethod", Class.class, String.class, Class[].class);
        findMethodWithTypes.setAccessible(true);
        Method repeatByTypes = (Method) findMethodWithTypes.invoke(null, LocalMethods.class, "repeat", new Class<?>[]{String.class, int.class});
        assertThat(repeatByTypes.getName()).isEqualTo("repeat");

        Method findMethodWithTypeNames = utilClass.getDeclaredMethod("findMethod", Class.class, String.class, String[].class);
        findMethodWithTypeNames.setAccessible(true);
        Method repeatByNames = (Method) findMethodWithTypeNames.invoke(null, LocalMethods.class, "repeat", new String[]{"java.lang.String", "int"});
        assertThat(repeatByNames).isEqualTo(repeatByTypes);

        Method findMethodAnyArgs = utilClass.getDeclaredMethod("findMethodAnyArgs", Class.class, String.class);
        findMethodAnyArgs.setAccessible(true);
        Method ping = (Method) findMethodAnyArgs.invoke(null, LocalMethods.class, "ping");
        assertThat(ping.getName()).isEqualTo("ping");

        Method invokeMethod = utilClass.getDeclaredMethod("invokeMethod", Method.class, Object[].class);
        invokeMethod.setAccessible(true);
        Object repeated = invokeMethod.invoke(null, new Object[]{repeatByTypes, new Object[]{"na", 2}});
        Object pinged = invokeMethod.invoke(null, new Object[]{ping, new Object[0]});

        assertThat(repeated).isEqualTo("nana");
        assertThat(pinged).isEqualTo("pong");
    }

    public static final class LocalMethods {
        public static String repeat(String value, int count) {
            return value.repeat(count);
        }

        public static String ping() {
            return "pong";
        }
    }
}
