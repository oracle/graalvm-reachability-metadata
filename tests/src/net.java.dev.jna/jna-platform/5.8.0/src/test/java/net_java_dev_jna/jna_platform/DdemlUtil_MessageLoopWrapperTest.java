/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import com.sun.jna.platform.win32.Ddeml;
import com.sun.jna.platform.win32.DdemlUtil;
import com.sun.jna.platform.win32.User32Util;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

public class DdemlUtil_MessageLoopWrapperTest {
    @Test
    void wrapsReturnedConnectionsWithoutStartingNativeMessageLoop() {
        ImmediateMessageLoopThread loopThread = new ImmediateMessageLoopThread();
        Ddeml.HCONV conversation = new Ddeml.HCONV();
        DdemlUtil.IDdeConnection delegateConnection = createConnectionDelegate(conversation);
        DdemlUtil.IDdeClient wrappedClient = createMessageLoopWrapperProxy(
                DdemlUtil.IDdeClient.class,
                loopThread,
                createClientDelegate(delegateConnection)
        );

        DdemlUtil.IDdeConnection wrappedConnection = wrappedClient.wrap(conversation);

        assertThat(wrappedConnection).isNotNull();
        assertThat(wrappedConnection.getConv()).isSameAs(conversation);
    }

    private static DdemlUtil.IDdeClient createClientDelegate(DdemlUtil.IDdeConnection connection) {
        InvocationHandler handler = (proxy, method, args) -> {
            String methodName = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, methodName, args);
            }
            if ("wrap".equals(methodName)) {
                return connection;
            }
            throw new UnsupportedOperationException(methodName);
        };
        return (DdemlUtil.IDdeClient) Proxy.newProxyInstance(
                DdemlUtil.IDdeClient.class.getClassLoader(),
                new Class<?>[]{DdemlUtil.IDdeClient.class},
                handler
        );
    }

    private static DdemlUtil.IDdeConnection createConnectionDelegate(Ddeml.HCONV conversation) {
        InvocationHandler handler = (proxy, method, args) -> {
            String methodName = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, methodName, args);
            }
            if ("getConv".equals(methodName)) {
                return conversation;
            }
            if ("close".equals(methodName)) {
                return null;
            }
            throw new UnsupportedOperationException(methodName);
        };
        return (DdemlUtil.IDdeConnection) Proxy.newProxyInstance(
                DdemlUtil.IDdeConnection.class.getClassLoader(),
                new Class<?>[]{DdemlUtil.IDdeConnection.class},
                handler
        );
    }

    private static Object invokeObjectMethod(Object proxy, String methodName, Object[] args) {
        if ("toString".equals(methodName)) {
            return proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
        }
        if ("hashCode".equals(methodName)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(methodName)) {
            return proxy == args[0];
        }
        throw new UnsupportedOperationException(methodName);
    }

    private static <T> T createMessageLoopWrapperProxy(Class<T> interfaceType, User32Util.MessageLoopThread loopThread, T delegate) {
        InvocationHandler handler = createMessageLoopWrapper(loopThread, delegate);
        Object proxy = Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[]{interfaceType}, handler);
        return interfaceType.cast(proxy);
    }

    private static InvocationHandler createMessageLoopWrapper(User32Util.MessageLoopThread loopThread, Object delegate) {
        try {
            Class<?> wrapperClass = Class.forName("com.sun.jna.platform.win32.DdemlUtil$MessageLoopWrapper");
            Constructor<?> constructor = wrapperClass.getDeclaredConstructor(User32Util.MessageLoopThread.class, Object.class);
            constructor.setAccessible(true);
            return (InvocationHandler) constructor.newInstance(loopThread, delegate);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class ImmediateMessageLoopThread extends User32Util.MessageLoopThread {
        @Override
        public <V> V runOnThread(Callable<V> callable) throws Exception {
            return callable.call();
        }
    }
}
