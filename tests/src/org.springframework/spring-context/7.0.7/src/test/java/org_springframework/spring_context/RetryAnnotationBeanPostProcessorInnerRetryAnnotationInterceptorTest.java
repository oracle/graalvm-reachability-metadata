/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.resilience.annotation.RetryAnnotationBeanPostProcessor;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.resilience.retry.MethodRetryPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RetryAnnotationBeanPostProcessorInnerRetryAnnotationInterceptorTest {
    @Test
    void instantiatesPredicateAndRetriesOnlyMatchingFailures() {
        RetryAnnotationBeanPostProcessor processor = new RetryAnnotationBeanPostProcessor();
        RemoteInventory target = new RemoteInventory();
        Inventory inventory = (Inventory) processor.postProcessAfterInitialization(target, "inventory");
        assertEquals("available", inventory.lookup());
        assertEquals(3, target.attempts);
        assertThrows(IllegalArgumentException.class, inventory::reject);
        assertEquals(1, target.rejections);
    }

    public interface Inventory {
        String lookup();
        void reject();
    }

    @Retryable(predicate = TransientFailure.class, maxRetries = 3, delay = 0, jitter = 0)
    public static class RemoteInventory implements Inventory {
        private int attempts;
        private int rejections;

        @Override
        public String lookup() {
            if (++attempts < 3) {
                throw new IllegalStateException("temporarily unavailable");
            }
            return "available";
        }

        @Override
        public void reject() {
            rejections++;
            throw new IllegalArgumentException("invalid request");
        }
    }

    public static class TransientFailure implements MethodRetryPredicate {
        @Override
        public boolean shouldRetry(Method method, Throwable failure) {
            return failure instanceof IllegalStateException && method.getName().equals("lookup");
        }
    }
}
