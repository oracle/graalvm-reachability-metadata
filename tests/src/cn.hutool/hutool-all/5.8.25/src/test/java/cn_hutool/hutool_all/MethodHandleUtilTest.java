/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.lang.reflect.MethodHandleUtil;
import cn.hutool.core.util.ReflectUtil;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodHandleUtilTest {
    @Test
    void findsMethodHandlesForConstructorInstanceAndStaticMembers() throws Throwable {
        MethodHandle constructor = MethodHandleUtil.findConstructor(
                HandleTarget.class, String.class);
        assertThat(constructor).isNotNull();
        HandleTarget target = (HandleTarget) constructor.invoke("hutool");

        MethodHandle instanceMethod = MethodHandleUtil.findMethod(
                HandleTarget.class, "join", MethodType.methodType(String.class, String.class));
        assertThat(instanceMethod).isNotNull();
        String joined = (String) instanceMethod.bindTo(target).invoke("core");
        assertThat(joined).isEqualTo("hutool:core");

        MethodType staticMethodType = MethodType.methodType(
                String.class, String.class, int.class);
        MethodHandle staticMethod = MethodHandleUtil.findMethod(
                HandleTarget.class, "staticJoin", staticMethodType);
        assertThat(staticMethod).isNotNull();
        String staticJoined = (String) staticMethod.invoke("version", 25);
        assertThat(staticJoined).isEqualTo("version:25");
    }

    @Test
    void invokesMethodsFromReflectionObjectsAndReturnsNullForMissingLookup() {
        HandleTarget target = new HandleTarget("metadata");

        Method publicMethod = ReflectUtil.getMethod(HandleTarget.class, "join", String.class);
        String publicResult = MethodHandleUtil.invoke(target, publicMethod, "public");
        assertThat(publicResult).isEqualTo("metadata:public");

        String privateResult = MethodHandleUtil.invokeSpecial(target, "secret", "private");
        assertThat(privateResult).isEqualTo("secret:metadata:private");

        MethodHandle missingMethod = MethodHandleUtil.findMethod(
                HandleTarget.class, "missing", MethodType.methodType(String.class));
        assertThat(missingMethod).isNull();
    }

    public static class HandleTarget {
        private final String prefix;

        public HandleTarget(String prefix) {
            this.prefix = prefix;
        }

        public String join(String value) {
            return prefix + ":" + value;
        }

        public static String staticJoin(String value, int number) {
            return value + ":" + number;
        }

        private String secret(String value) {
            return "secret:" + prefix + ":" + value;
        }
    }
}
