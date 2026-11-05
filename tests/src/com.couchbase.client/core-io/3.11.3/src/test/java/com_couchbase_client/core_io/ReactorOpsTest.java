/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.util.ReactorOps;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReactorOpsTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Values proxy = ReactorOps.proxyToPublishOnSuppliedScheduler(new Source(), Values.class, Schedulers::immediate);
        assertEquals("payload", proxy.values().block(Duration.ofSeconds(10)));
        assertEquals("source", proxy.name());
    }

    public interface Values {
        Mono<String> values();
        String name();
    }

    public static class Source implements Values {
        @Override
        public Mono<String> values() {
            return Mono.just("payload");
        }

        @Override
        public String name() {
            return "source";
        }
    }
}
