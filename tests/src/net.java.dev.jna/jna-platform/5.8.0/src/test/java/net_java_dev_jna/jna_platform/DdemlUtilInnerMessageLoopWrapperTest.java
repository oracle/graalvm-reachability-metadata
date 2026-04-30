/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.platform.win32.DdemlUtil.DdeClient;
import com.sun.jna.platform.win32.DdemlUtil.IDdeClient;
import com.sun.jna.platform.win32.DdemlUtil.IDdeConnection;
import com.sun.jna.platform.win32.User32Util.MessageLoopThread;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

public class DdemlUtilInnerMessageLoopWrapperTest {
    @Test
    void wrapReturnsMessageLoopProxyForDdeConnections() throws Exception {
        IDdeClient client = proxiedClient(new DdeClient());

        IDdeConnection connection = client.wrap(null);

        assertThat(connection).isNotNull();
        assertThat(Proxy.isProxyClass(connection.getClass())).isTrue();
        assertThat(connection.getConv()).isNull();
    }

    private static IDdeClient proxiedClient(DdeClient delegate) throws Exception {
        InvocationHandler wrapper = newMessageLoopWrapper(delegate);
        return (IDdeClient) Proxy.newProxyInstance(
            DdeClient.class.getClassLoader(),
            new Class<?>[] { IDdeClient.class },
            wrapper
        );
    }

    private static InvocationHandler newMessageLoopWrapper(DdeClient delegate) throws Exception {
        Class<?> wrapperClass = Class.forName("com.sun.jna.platform.win32.DdemlUtil$MessageLoopWrapper");
        Constructor<?> constructor = wrapperClass.getDeclaredConstructor(MessageLoopThread.class, Object.class);
        constructor.setAccessible(true);
        return (InvocationHandler) constructor.newInstance(new SynchronousMessageLoopThread(), delegate);
    }

    private static final class SynchronousMessageLoopThread extends MessageLoopThread {
        @Override
        public <V> V runOnThread(Callable<V> callable) throws Exception {
            return callable.call();
        }

        @Override
        public <V> Future<V> runAsync(Callable<V> command) {
            try {
                return CompletableFuture.completedFuture(command.call());
            } catch (Exception ex) {
                CompletableFuture<V> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(ex);
                return failedFuture;
            }
        }
    }
}
