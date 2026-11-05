/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.jetty_websocket_core_common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jetty.websocket.core.util.ReflectUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectUtilsTest {
    @Test
    void findsPublicCallbackBySignature() {
        Method method = ReflectUtils.findMethod(Endpoint.class, "message", String.class);
        assertNotNull(method);
        assertEquals("message", method.getName());
        assertEquals(void.class, method.getReturnType());
        assertEquals(List.of(String.class), List.of(method.getParameterTypes()));
        assertTrue(ReflectUtils.toString(Endpoint.class, method).endsWith("#message(java.lang.String)"));
        assertNull(ReflectUtils.findMethod(Endpoint.class, "message", int.class));
    }

    @Test
    void discoversAnnotatedCallbacksAcrossHierarchy() {
        Method[] methods = ReflectUtils.findAnnotatedMethods(Endpoint.class, MessageCallback.class);
        assertNotNull(methods);
        assertEquals(List.of("message", "opened"),
                Arrays.stream(methods).map(Method::getName).sorted().toList());
        Method opened = ReflectUtils.findAnnotatedMethod(BaseEndpoint.class, MessageCallback.class);
        assertNotNull(opened);
        assertEquals("opened", opened.getName());
        assertNull(ReflectUtils.findAnnotatedMethods(NoCallbacks.class, MessageCallback.class));
    }

    @Test
    void checksPublicDefaultConstruction() {
        assertTrue(ReflectUtils.isDefaultConstructable(Endpoint.class));
        assertFalse(ReflectUtils.isDefaultConstructable(NeedsArgument.class));
        assertFalse(ReflectUtils.isDefaultConstructable(AbstractEndpoint.class));
    }

    @Test
    void resolvesGenericArrayPayloadType() {
        Type payloadType = ReflectUtils.findGenericTypeFor(BatchedEndpoint.class, Payload.class);
        assertInstanceOf(GenericArrayType.class, payloadType);
        assertEquals(List[].class, ReflectUtils.getClassFromType(payloadType));
        assertEquals(String.class, ReflectUtils.getClassFromType(String.class));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface MessageCallback {
    }

    public static class BaseEndpoint {
        @MessageCallback
        public void opened() {
        }
    }

    public static class Endpoint extends BaseEndpoint {
        @MessageCallback
        public void message(String text) {
        }
    }

    public static class NoCallbacks {
    }

    public static class NeedsArgument {
        public NeedsArgument(String name) {
        }
    }

    public abstract static class AbstractEndpoint {
    }

    public interface Payload<T> {
    }

    public static class BatchedEndpoint implements Payload<List<String>[]> {
    }
}
