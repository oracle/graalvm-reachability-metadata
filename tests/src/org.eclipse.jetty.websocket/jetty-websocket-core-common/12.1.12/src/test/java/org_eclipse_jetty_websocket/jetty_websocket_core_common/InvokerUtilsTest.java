/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.jetty_websocket_core_common;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import org.eclipse.jetty.websocket.core.util.InvokerUtils;
import org.eclipse.jetty.websocket.core.util.InvokerUtils.Arg;
import org.eclipse.jetty.websocket.core.util.ReflectUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(120)
public class InvokerUtilsTest {
    @Test
    void adaptsInheritedCallbackToConcreteReceiverAndReorderedArguments() throws Throwable {
        Method callback = ReflectUtils.findMethod(Endpoint.class, "message", int.class, String.class);
        assertNotNull(callback);
        MethodHandle invoker = InvokerUtils.mutatedInvoker(MethodHandles.lookup(), Endpoint.class, callback,
                new Arg(String.class).required(), new Arg(boolean.class), new Arg(int.class).required());

        assertEquals(MethodType.methodType(String.class, Endpoint.class, String.class, boolean.class, int.class),
                invoker.type());
        Endpoint endpoint = new Endpoint();
        String result = (String) invoker.invokeExact(endpoint, "hello", true, 7);
        assertEquals("7:hello", result);
        String next = (String) invoker.invokeExact(endpoint, "world", false, 8);
        assertEquals("8:world", next);
    }

    @Test
    void mapsNamedCallbackArgumentsIndependentlyOfTheirOrder() throws Throwable {
        Method callback = ReflectUtils.findMethod(Endpoint.class, "route", String.class, String.class);
        assertNotNull(callback);
        MethodHandle invoker = InvokerUtils.mutatedInvoker(MethodHandles.lookup(), Endpoint.class, callback,
                (method, type, index) -> new Arg(type, index == 0 ? "room" : "body"),
                new String[]{"body", "room", "unused"});

        assertEquals(MethodType.methodType(String.class, Endpoint.class, String.class, String.class, String.class),
                invoker.type());
        Endpoint endpoint = new Endpoint();
        String result = (String) invoker.invokeExact(endpoint, "hello", "lobby", "ignored");
        assertEquals("lobby/hello", result);
    }

    public static class BaseEndpoint {
        public String message(int sequence, String text) {
            return sequence + ":" + text;
        }
    }

    public static class Endpoint extends BaseEndpoint {
        public String route(String room, String body) {
            return room + "/" + body;
        }
    }
}
