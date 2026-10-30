/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.constructs.asynchronous.AsynchronousCommandExecutor;
import net.sf.ehcache.constructs.asynchronous.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class AsynchronousCommandExecutorTest {
    @TempDir
    Path directory;

    @Test
    void executesQueuedCommandAndRemovesCompletedMessage() throws Exception {
        String xml = """
                <ehcache>
                  <diskStore path="java.io.tmpdir"/>
                  <defaultCache maxElementsInMemory="20" eternal="true" overflowToDisk="false"/>
                  <cache name="net.sf.ehcache.constructs.asynchronous.MessageCache"
                         maxElementsInMemory="20" eternal="true" overflowToDisk="false"/>
                </ehcache>
                """;
        CacheManager manager = CacheManager.create(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        AsynchronousCommandExecutor executor = AsynchronousCommandExecutor.getInstance();
        CompletionCommand.completed = new CountDownLatch(1);
        try {
            String uid = executor.queueForExecution(new CompletionCommand());
            assertFalse(uid.isEmpty());
            assertTrue(CompletionCommand.completed.await(15, TimeUnit.SECONDS));
            TestSupport.await(() -> executor.countCachedPublishCommands() == 0);
            assertNull(executor.getMessageCache().get(uid));
        } finally {
            executor.dispose();
            manager.shutdown();
        }
    }

    public static final class CompletionCommand implements Command {
        private static final long serialVersionUID = 1L;
        static CountDownLatch completed;

        @Override
        public void execute() {
            completed.countDown();
        }

        @Override
        public Class[] getThrowablesToRetryOn() {
            return null;
        }

        @Override
        public int getNumberOfAttempts() {
            return 1;
        }

        @Override
        public int getDelayBetweenAttemptsInSeconds() {
            return 0;
        }
    }
}
