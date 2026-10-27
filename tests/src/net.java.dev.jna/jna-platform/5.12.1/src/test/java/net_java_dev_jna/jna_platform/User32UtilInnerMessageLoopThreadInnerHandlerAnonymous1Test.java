/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sun.jna.platform.win32.User32Util.MessageLoopThread;

public class User32UtilInnerMessageLoopThreadInnerHandlerAnonymous1Test {
    @Test
    public void handlerInvokesDelegateThroughDispatchedCallable() {
        InlineMessageLoopThread messageLoopThread = new InlineMessageLoopThread();
        CountingGreeter delegate = new CountingGreeter();
        GreetingService proxy = (GreetingService) Proxy.newProxyInstance(
                GreetingService.class.getClassLoader(),
                new Class<?>[] {GreetingService.class},
                messageLoopThread.new Handler(delegate));

        String greeting = proxy.greet("JNA");

        assertThat(greeting).isEqualTo("Hello, JNA");
        assertThat(delegate.invocationCount()).isEqualTo(1);
        assertThat(messageLoopThread.dispatchedCalls()).isEqualTo(1);
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static final class CountingGreeter implements GreetingService {
        private final AtomicInteger invocationCount = new AtomicInteger();

        @Override
        public String greet(String name) {
            invocationCount.incrementAndGet();
            return "Hello, " + name;
        }

        public int invocationCount() {
            return invocationCount.get();
        }
    }

    private static final class InlineMessageLoopThread extends MessageLoopThread {
        private final AtomicInteger dispatchedCalls = new AtomicInteger();

        @Override
        public <V> V runOnThread(Callable<V> callable) throws Exception {
            dispatchedCalls.incrementAndGet();
            return callable.call();
        }

        int dispatchedCalls() {
            return dispatchedCalls.get();
        }
    }
}
