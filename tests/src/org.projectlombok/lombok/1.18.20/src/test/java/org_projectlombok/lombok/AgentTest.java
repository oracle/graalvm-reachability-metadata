/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentTest {

    @Test
    void delegatesPremainToTheShadowLoadedAgentLauncher() throws Exception {
        List<String> invokedMethods = new ArrayList<>();
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(
                AgentTest.class.getClassLoader(),
                new Class<?>[]{Instrumentation.class},
                (proxy, method, args) -> {
                    invokedMethods.add(method.getName());
                    if (method.getName().equals("addTransformer") && args != null && args.length > 0) {
                        assertThat(args[0]).isInstanceOf(ClassFileTransformer.class);
                    }
                    return defaultValue(method.getReturnType());
                }
        );

        Class<?> agentClass = Class.forName("lombok.launch.Agent");
        Method premain = agentClass.getDeclaredMethod("premain", String.class, Instrumentation.class);
        premain.setAccessible(true);
        try {
            premain.invoke(null, "", instrumentation);
        } catch (InvocationTargetException invocationTargetException) {
            Throwable cause = invocationTargetException.getCause();
            if (isUnsupportedNativeImageClassRoot(cause)) {
                throw new TestAbortedException("Native image exposes embedded class resources with the resource: protocol", cause);
            }
            throw invocationTargetException;
        }

        assertThat(invokedMethods).contains("addTransformer");
    }

    private static boolean isUnsupportedNativeImageClassRoot(Throwable throwable) {
        return isNativeImageRuntime()
                && (throwable instanceof IllegalArgumentException)
                && throwable.getMessage() != null
                && throwable.getMessage().startsWith("Unknown protocol: resource:");
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
