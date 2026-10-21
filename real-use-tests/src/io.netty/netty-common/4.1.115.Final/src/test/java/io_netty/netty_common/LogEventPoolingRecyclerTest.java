/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.Recycler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the `Recycler` object-pooling subsystem of netty-common: thread-local pools that hand
 * out recycled instances through `get`, `Recycler.Handle` recycling from the owning and from
 * foreign threads, the double-recycle guard, the pooling ratio and a disabled pool.
 *
 * A log ingestion service parses every incoming line into a `LogEvent` taken from a recycler
 * instead of allocating a new one. The parser returns an event to the pool once it is written,
 * and the writer thread may do so on behalf of the ingest thread. Returning an event twice is a
 * bug the pool reports, a pool tuned with a ratio only keeps every n-th fresh event, and a pool
 * configured with zero capacity allocates a new event for every line.
 */
class LogEventPoolingRecyclerTest {

    private static final long WAIT_SECONDS = 10;

    private ExecutorService writerThread;

    @BeforeEach
    void startWriter() {
        writerThread = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void stopWriter() throws InterruptedException {
        writerThread.shutdownNow();
        assertThat(writerThread.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void parserReusesTheEventItReturnedOnTheSameThread() {
        LogEventPool pool = new LogEventPool(64, 1);
        LogParser parser = new LogParser(pool);

        LogEvent first = parser.parse("WARN disk usage at 91%");
        assertThat(first.level).isEqualTo("WARN");
        assertThat(first.message).isEqualTo("disk usage at 91%");
        assertThat(first.sequence).isEqualTo(1);
        first.release();
        assertThat(first.level).isNull();
        assertThat(first.message).isNull();

        LogEvent second = parser.parse("INFO backup completed");

        assertThat(second).isSameAs(first);
        assertThat(second.level).isEqualTo("INFO");
        assertThat(second.message).isEqualTo("backup completed");
        assertThat(second.sequence).isEqualTo(2);
        assertThat(pool.allocations()).isEqualTo(1);
    }

    @Test
    void eventsInFlightAreDistinctAndAllReturnToThePool() {
        LogEventPool pool = new LogEventPool(64, 1);
        LogParser parser = new LogParser(pool);

        List<LogEvent> batch = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            batch.add(parser.parse("DEBUG tick " + i));
        }
        Set<LogEvent> distinct = identitySet(batch);
        assertThat(distinct).hasSize(5);
        assertThat(pool.allocations()).isEqualTo(5);

        batch.forEach(LogEvent::release);
        List<LogEvent> reused = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            reused.add(parser.parse("DEBUG tock " + i));
        }

        assertThat(identitySet(reused)).hasSize(5).allMatch(distinct::contains);
        assertThat(reused).extracting(event -> event.message)
                .containsExactly("tock 0", "tock 1", "tock 2", "tock 3", "tock 4");
        assertThat(pool.allocations()).isEqualTo(5);
    }

    @Test
    void writerThreadReturnsEventsToTheIngestThreadPool() throws Exception {
        LogEventPool pool = new LogEventPool(64, 1);
        LogParser parser = new LogParser(pool);
        List<String> written = Collections.synchronizedList(new ArrayList<>());

        LogEvent event = parser.parse("ERROR connection refused");
        String line = writerThread.submit(() -> {
            String rendered = event.render();
            written.add(rendered);
            event.release();
            return rendered;
        }).get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(line).isEqualTo("#1 [ERROR] connection refused");
        assertThat(written).containsExactly("#1 [ERROR] connection refused");
        LogEvent next = parser.parse("INFO retry succeeded");
        assertThat(next).isSameAs(event);
        assertThat(next.render()).isEqualTo("#2 [INFO] retry succeeded");
        assertThat(pool.allocations()).isEqualTo(1);
    }

    @Test
    void returningAnEventTwiceIsRejected() {
        LogEventPool pool = new LogEventPool(64, 1);
        LogParser parser = new LogParser(pool);

        LogEvent event = parser.parse("WARN slow query");
        event.release();

        assertThatThrownBy(event::release)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Object has been recycled already.");
        LogEvent next = parser.parse("INFO query finished");
        assertThat(next).isSameAs(event);
        assertThat(pool.allocations()).isEqualTo(1);
    }

    @Test
    void poolWithRatioOnlyKeepsEveryFourthFreshEvent() {
        LogEventPool pool = new LogEventPool(64, 4);
        LogParser parser = new LogParser(pool);

        List<LogEvent> firstBurst = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            firstBurst.add(parser.parse("INFO burst-1 " + i));
        }
        assertThat(pool.allocations()).isEqualTo(8);
        firstBurst.forEach(LogEvent::release);

        List<LogEvent> secondBurst = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            secondBurst.add(parser.parse("INFO burst-2 " + i));
        }

        Set<LogEvent> pooledFromFirstBurst = identitySet(List.of(firstBurst.get(0), firstBurst.get(4)));
        List<LogEvent> reused = secondBurst.stream()
                .filter(identitySet(firstBurst)::contains)
                .toList();
        assertThat(reused).hasSize(2).allMatch(pooledFromFirstBurst::contains);
        assertThat(pool.allocations()).isEqualTo(14);
    }

    @Test
    void poolWithoutCapacityAllocatesForEveryLine() {
        LogEventPool pool = new LogEventPool(0, 1);
        LogParser parser = new LogParser(pool);

        LogEvent first = parser.parse("INFO first line");
        first.release();
        first.release();
        LogEvent second = parser.parse("INFO second line");

        assertThat(second).isNotSameAs(first);
        assertThat(second.message).isEqualTo("second line");
        assertThat(pool.allocations()).isEqualTo(2);
    }

    private static Set<LogEvent> identitySet(List<LogEvent> events) {
        Set<LogEvent> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(events);
        return set;
    }

    /// A parsed log line whose instance is owned by the recycler that created it.
    private static final class LogEvent {

        private final Recycler.Handle<LogEvent> handle;

        private long sequence;

        private String level;

        private String message;

        private LogEvent(Recycler.Handle<LogEvent> handle) {
            this.handle = handle;
        }

        String render() {
            return "#" + sequence + " [" + level + "] " + message;
        }

        /// Clears the event and hands it back to its pool.
        void release() {
            sequence = 0;
            level = null;
            message = null;
            handle.recycle(this);
        }
    }

    /// The recycler that creates events, counting how many it had to allocate.
    private static final class LogEventPool extends Recycler<LogEvent> {

        private final AtomicInteger allocations = new AtomicInteger();

        LogEventPool(int maxCapacityPerThread, int ratio) {
            super(maxCapacityPerThread, ratio, 16);
        }

        @Override
        protected LogEvent newObject(Handle<LogEvent> handle) {
            allocations.incrementAndGet();
            return new LogEvent(handle);
        }

        int allocations() {
            return allocations.get();
        }
    }

    /// Turns `LEVEL message` lines into pooled events numbered in arrival order.
    private static final class LogParser {

        private final LogEventPool pool;

        private long nextSequence = 1;

        LogParser(LogEventPool pool) {
            this.pool = pool;
        }

        LogEvent parse(String line) {
            int space = line.indexOf(' ');
            LogEvent event = pool.get();
            event.sequence = nextSequence++;
            event.level = line.substring(0, space);
            event.message = line.substring(space + 1);
            return event;
        }
    }
}
