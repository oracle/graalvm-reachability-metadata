/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.util.concurrent.SimpleTimeLimiter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleTimeLimiterTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Service service = SimpleTimeLimiter.create(executor).newProxy(new ServiceImpl(), Service.class, 10, TimeUnit.SECONDS);
            assertEquals("result:input", service.lookup("input"));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    public interface Service {
        String lookup(String input) throws InterruptedException;
    }

    public static class ServiceImpl implements Service {
        @Override
        public String lookup(String input) {
            return "result:" + input;
        }
    }
}
