/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class AgentTest {
    @Test
    void premainLaunchesLombokAgentThroughTheShadowClassLoader() throws Throwable {
        AtomicInteger registeredTransformers = new AtomicInteger();
        Instrumentation instrumentation = newInstrumentation(registeredTransformers);

        Class<?> agentClass = Class.forName("lombok.launch.Agent");
        Method premain = agentClass.getDeclaredMethod(
                "premain", String.class, Instrumentation.class);
        premain.setAccessible(true);

        try {
            premain.invoke(null, "", instrumentation);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }

        assertThat(registeredTransformers).hasValue(1);
    }

    private Instrumentation newInstrumentation(AtomicInteger registeredTransformers) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass().equals(Object.class)) {
                return handleObjectMethod(proxy, method, args);
            }
            if (method.getName().equals("addTransformer")) {
                registeredTransformers.incrementAndGet();
                return null;
            }
            return defaultValue(method.getReturnType());
        };
        return (Instrumentation) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {Instrumentation.class}, handler);
    }

    private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "TestInstrumentation";
            default -> throw new IllegalArgumentException(method.getName());
        };
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            if (returnType.equals(Class[].class)) {
                return new Class<?>[0];
            }
            return null;
        }
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.equals(char.class)) {
            return '\0';
        }
        if (returnType.equals(byte.class)) {
            return (byte) 0;
        }
        if (returnType.equals(short.class)) {
            return (short) 0;
        }
        if (returnType.equals(int.class)) {
            return 0;
        }
        if (returnType.equals(long.class)) {
            return 0L;
        }
        if (returnType.equals(float.class)) {
            return 0F;
        }
        if (returnType.equals(double.class)) {
            return 0D;
        }
        return null;
    }
}
