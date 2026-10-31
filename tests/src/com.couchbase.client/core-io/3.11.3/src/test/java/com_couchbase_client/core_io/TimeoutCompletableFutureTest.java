/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.org.xbill.DNS.ARecord;
import com.couchbase.client.core.deps.org.xbill.DNS.DClass;
import com.couchbase.client.core.deps.org.xbill.DNS.DohResolver;
import com.couchbase.client.core.deps.org.xbill.DNS.Message;
import com.couchbase.client.core.deps.org.xbill.DNS.Name;
import com.couchbase.client.core.deps.org.xbill.DNS.Record;
import com.couchbase.client.core.deps.org.xbill.DNS.Section;
import com.couchbase.client.core.deps.org.xbill.DNS.Type;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimeoutCompletableFutureTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ExecutorService executor = Executors.newCachedThreadPool();
        try (LocalDohServer server = new LocalDohServer()) {
            DohResolver resolver = new DohResolver(server.uri());
            resolver.setTimeout(Duration.ofSeconds(20));
            resolver.setUsePost(false);
            Message query = Message.newQuery(Record.newRecord(Name.fromString("metadata.example."), Type.A, DClass.IN));
            Message result = resolver.sendAsync(query, executor).toCompletableFuture().get(30, TimeUnit.SECONDS);
            assertEquals(1, result.getSection(Section.ANSWER).size());
            ARecord answer = (ARecord) result.getSection(Section.ANSWER).get(0);
            assertEquals("192.0.2.42", answer.getAddress().getHostAddress());
            assertEquals(query.getQuestion().getName(), answer.getName());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
