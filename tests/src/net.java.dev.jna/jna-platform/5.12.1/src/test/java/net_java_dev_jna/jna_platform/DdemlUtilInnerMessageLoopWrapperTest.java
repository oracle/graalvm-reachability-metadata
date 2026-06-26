/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sun.jna.platform.win32.Ddeml.HCONV;
import com.sun.jna.platform.win32.DdemlUtil.IDdeClient;
import com.sun.jna.platform.win32.DdemlUtil.IDdeConnection;
import com.sun.jna.platform.win32.User32Util.MessageLoopThread;

public class DdemlUtilInnerMessageLoopWrapperTest {
    @Test
    void wrapsReturnedDdeConnectionsAndDispatchesTheirMethodsThroughMessageLoop() throws Exception {
        RecordingMessageLoopThread loopThread = new RecordingMessageLoopThread();
        HCONV conversation = new HCONV();
        IDdeConnection connection = newConnection(conversation);
        IDdeClient delegate = newClientReturning(connection);
        IDdeClient client = newMessageLoopWrappedClient(loopThread, delegate);

        IDdeConnection wrappedConnection = client.wrap(conversation);

        assertThat(wrappedConnection).isNotSameAs(connection);
        assertThat(Proxy.isProxyClass(wrappedConnection.getClass())).isTrue();
        assertThat(wrappedConnection.getConv()).isSameAs(conversation);
        assertThat(loopThread.invocationCount()).isEqualTo(1);
    }

    private static IDdeClient newMessageLoopWrappedClient(MessageLoopThread loopThread, IDdeClient delegate)
            throws Exception {
        Class<?> wrapperClass = Class.forName("com.sun.jna.platform.win32.DdemlUtil$MessageLoopWrapper");
        Constructor<?> constructor = wrapperClass.getDeclaredConstructor(MessageLoopThread.class, Object.class);
        constructor.setAccessible(true);
        InvocationHandler handler = (InvocationHandler) constructor.newInstance(loopThread, delegate);
        return (IDdeClient) Proxy.newProxyInstance(IDdeClient.class.getClassLoader(), new Class<?>[]{IDdeClient.class},
                handler);
    }

    private static IDdeClient newClientReturning(IDdeConnection connection) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass().equals(Object.class)) {
                return invokeObjectMethod(proxy, method, args);
            }
            if (method.getName().equals("wrap")) {
                return connection;
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return (IDdeClient) Proxy.newProxyInstance(IDdeClient.class.getClassLoader(), new Class<?>[]{IDdeClient.class},
                handler);
    }

    private static IDdeConnection newConnection(HCONV conversation) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass().equals(Object.class)) {
                return invokeObjectMethod(proxy, method, args);
            }
            if (method.getName().equals("getConv")) {
                return conversation;
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return (IDdeConnection) Proxy.newProxyInstance(IDdeConnection.class.getClassLoader(),
                new Class<?>[]{IDdeConnection.class}, handler);
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
            default:
                throw new UnsupportedOperationException(method.toString());
        }
    }

    private static final class RecordingMessageLoopThread extends MessageLoopThread {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public <V> V runOnThread(Callable<V> callable) throws Exception {
            invocations.incrementAndGet();
            return callable.call();
        }

        int invocationCount() {
            return invocations.get();
        }
    }
}
