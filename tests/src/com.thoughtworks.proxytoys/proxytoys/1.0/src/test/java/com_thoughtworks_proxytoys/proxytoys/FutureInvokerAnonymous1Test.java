/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_proxytoys.proxytoys;

import com.thoughtworks.proxy.Invoker;
import com.thoughtworks.proxy.ProxyFactory;
import com.thoughtworks.proxy.factory.StandardProxyFactory;
import com.thoughtworks.proxy.toys.future.FutureInvoker;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FutureInvokerAnonymous1Test {
    @Test
    void nonVoidMethodCallIsInvokedAsynchronouslyAndSwapsInReturnedValue() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ProxyFactory proxyFactory = new StandardProxyFactory();
            List<String> target = Arrays.asList("alpha", "beta");
            Invoker invoker = new FutureInvoker(target, proxyFactory, executor);

            @SuppressWarnings("unchecked")
            List<String> futureList = proxyFactory.createProxy(invoker, List.class);
            Iterator<String> futureIterator = futureList.iterator();

            waitUntilIteratorIsReady(futureIterator);

            assertEquals("alpha", futureIterator.next());
            assertEquals("beta", futureIterator.next());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void waitUntilIteratorIsReady(Iterator<String> futureIterator) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!futureIterator.hasNext() && System.nanoTime() < deadlineNanos) {
            Thread.sleep(10);
        }
        assertTrue(futureIterator.hasNext(), "future iterator should be hot-swapped with the target iterator");
    }
}
