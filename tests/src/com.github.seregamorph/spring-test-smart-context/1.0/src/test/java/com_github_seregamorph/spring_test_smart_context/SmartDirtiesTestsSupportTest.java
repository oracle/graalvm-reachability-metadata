/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_seregamorph.spring_test_smart_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.seregamorph.testsmartcontext.jupiter.AbstractJUnitSpringIntegrationTest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

public class SmartDirtiesTestsSupportTest {

    private static final AtomicBoolean orderingResourceProbeStarted = new AtomicBoolean();
    private static final AtomicBoolean orderingResourceValidated = new AtomicBoolean();
    private static final AtomicInteger contextsCreated = new AtomicInteger();
    private static final AtomicInteger contextsClosed = new AtomicInteger();

    @ContextConfiguration(classes = SharedConfiguration.class, loader = OrderingProbeContextLoader.class)
    public static class FirstSharedContextCase extends AbstractJUnitSpringIntegrationTest {

        @Test
        void createsSharedContext() {
            assertTrue(orderingResourceValidated.get());
            assertEquals(1, contextsCreated.get());
            assertEquals(0, contextsClosed.get());
        }
    }

    @ContextConfiguration(classes = SharedConfiguration.class, loader = OrderingProbeContextLoader.class)
    public static class SecondSharedContextCase extends AbstractJUnitSpringIntegrationTest {

        @Test
        void reusesSharedContext() {
            assertEquals(1, contextsCreated.get());
            assertEquals(0, contextsClosed.get());
        }
    }

    @ContextConfiguration(classes = IsolatedConfiguration.class, loader = OrderingProbeContextLoader.class)
    public static class IsolatedContextCase extends AbstractJUnitSpringIntegrationTest {

        @Test
        void runsAfterSharedContextIsClosed() {
            assertEquals(2, contextsCreated.get());
            assertEquals(1, contextsClosed.get());
        }
    }

    public static class OrderingProbeContextLoader extends AnnotationConfigContextLoader {

        public OrderingProbeContextLoader() {
            if (orderingResourceProbeStarted.compareAndSet(false, true)) {
                validateOrderingResource();
                orderingResourceValidated.set(true);
            }
        }

        private static void validateOrderingResource() {
            TestContextManager testContextManager = new TestContextManager(OrderingProbeTarget.class);
            try {
                testContextManager.afterTestClass();
                throw new AssertionError("Expected test ordering validation to reject an early listener callback");
            } catch (IllegalStateException e) {
                assertEquals("Test ordering is not initialized or failed", e.getMessage());
            } catch (Exception e) {
                throw new AssertionError("Unexpected test context callback exception", e);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class SharedConfiguration {

        @Bean
        ContextLifecycle sharedContextLifecycle() {
            return new ContextLifecycle();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class IsolatedConfiguration {

        @Bean
        ContextLifecycle isolatedContextLifecycle() {
            return new ContextLifecycle();
        }
    }

    public static class ContextLifecycle implements DisposableBean {

        public ContextLifecycle() {
            contextsCreated.incrementAndGet();
        }

        @Override
        public void destroy() {
            contextsClosed.incrementAndGet();
        }
    }

    private static class OrderingProbeTarget {
    }
}
