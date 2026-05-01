/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_proxytoys.proxytoys;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.proxy.ProxyFactory;
import com.thoughtworks.proxy.factory.StandardProxyFactory;
import com.thoughtworks.proxy.toys.future.FutureInvoker;
import java.io.Serializable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class FutureInvokerAnonymous1Test {
    @Test
    void callableInvokesTargetMethodAndHotSwapsReturnedProxy() throws InterruptedException {
        BlockingGreetingService target = new BlockingGreetingService();
        ProxyFactory proxyFactory = new StandardProxyFactory();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        GreetingService proxy = proxyFactory.createProxy(
                new FutureInvoker(target, proxyFactory, executor), GreetingService.class);

        try {
            Greeting futureGreeting = proxy.greetingFor("Ada");

            assertThat(target.awaitInvocation()).isTrue();
            assertThat(futureGreeting.message()).isEmpty();

            target.completeInvocation();

            assertThat(awaitMessage(futureGreeting, "Hello Ada")).isEqualTo("Hello Ada");
        } finally {
            target.completeInvocation();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static String awaitMessage(Greeting greeting, String expectedMessage) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String actualMessage = greeting.message();
        while (!expectedMessage.equals(actualMessage) && System.nanoTime() < deadline) {
            Thread.sleep(25);
            actualMessage = greeting.message();
        }
        return actualMessage;
    }

    public interface GreetingService extends Serializable {
        Greeting greetingFor(String name);
    }

    public interface Greeting extends Serializable {
        String message();
    }

    public static final class BlockingGreetingService implements GreetingService {
        private static final long serialVersionUID = 1L;

        private final CountDownLatch invocationStarted = new CountDownLatch(1);
        private final CountDownLatch mayReturn = new CountDownLatch(1);

        @Override
        public Greeting greetingFor(String name) {
            invocationStarted.countDown();
            awaitReturnPermission();
            return new GreetingResult("Hello " + name);
        }

        boolean awaitInvocation() throws InterruptedException {
            return invocationStarted.await(5, TimeUnit.SECONDS);
        }

        void completeInvocation() {
            mayReturn.countDown();
        }

        private void awaitReturnPermission() {
            try {
                if (!mayReturn.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Future invocation was not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Future invocation was interrupted", e);
            }
        }
    }

    public static final class GreetingResult implements Greeting {
        private static final long serialVersionUID = 1L;

        private final String message;

        public GreetingResult(String message) {
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }
}
