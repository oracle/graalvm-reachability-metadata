/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alipay_sofa.jraft_core;

import com.alipay.sofa.jraft.util.JRaftServiceLoader;
import com.alipay.sofa.jraft.util.JRaftSignalHandler;
import com.alipay.sofa.jraft.util.timer.RaftTimerFactory;
import com.alipay.sofa.jraft.util.timer.Timeout;
import com.alipay.sofa.jraft.util.timer.Timer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JRaftServiceLoaderInnerLazyIteratorTest {
    @Test
    void lazilyDiscoversSignalHandlersFromBundledServiceDescriptor() {
        Iterator<Class<JRaftSignalHandler>> providers = JRaftServiceLoader.load(JRaftSignalHandler.class)
                .classIterator();
        Set<String> names = new HashSet<>();
        while (providers.hasNext()) {
            assertTrue(providers.hasNext());
            names.add(providers.next().getName());
        }
        assertEquals(Set.of("com.alipay.sofa.jraft.NodeDescribeSignalHandler",
                "com.alipay.sofa.jraft.NodeMetricsSignalHandler",
                "com.alipay.sofa.jraft.ThreadPoolMetricsSignalHandler"), names);
        assertFalse(providers.hasNext());
        Set<String> instantiated = new HashSet<>();
        for (JRaftSignalHandler handler : JRaftServiceLoader.load(JRaftSignalHandler.class)) {
            instantiated.add(handler.getClass().getName());
        }
        assertEquals(names, instantiated);
    }

    @Test
    void discoversTimerFactoryWithDefaultAndSystemClassLoaders() throws Exception {
        JRaftServiceLoader<RaftTimerFactory> loader = JRaftServiceLoader.load(RaftTimerFactory.class, null);
        Iterator<RaftTimerFactory> providers = loader.iterator();
        assertTrue(providers.hasNext());
        RaftTimerFactory factory = providers.next();
        assertFalse(providers.hasNext());
        assertSame(factory, loader.iterator().next());
        Timer timer = factory.createTimer("metadata-timer");
        try {
            CompletableFuture<Timeout> fired = new CompletableFuture<>();
            Timeout scheduled = timer.newTimeout(fired::complete, 0, TimeUnit.MILLISECONDS);
            assertSame(scheduled, fired.get(30, TimeUnit.SECONDS));
            assertTrue(scheduled.isExpired());
            assertSame(timer, scheduled.timer());
        } finally {
            timer.stop();
        }
    }
}
