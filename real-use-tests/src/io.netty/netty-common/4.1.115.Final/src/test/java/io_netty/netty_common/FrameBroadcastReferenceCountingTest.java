/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the reference-counting subsystem of netty-common: `AbstractReferenceCounted` objects
 * whose last `release` deallocates them, `ReferenceCountUtil` helpers that retain, touch and
 * release arbitrary messages, and the `IllegalReferenceCountException` raised on misuse.
 *
 * A video relay owns a small pool of frame slots. Every published frame borrows a slot, the
 * relay retains the frame once per subscriber and hands it to them on worker threads, and the
 * slot only returns to the pool once every subscriber and the relay itself have released their
 * reference. Touch hints record where a frame travelled, plain messages pass through the helpers
 * untouched, and a frame that is retained or released after it was freed is rejected while
 * `safeRelease` only logs the mistake.
 */
class FrameBroadcastReferenceCountingTest {

    private static final long WAIT_SECONDS = 10;

    private SlotPool pool;

    private ExecutorService subscriberThreads;

    @BeforeEach
    void startRelay() {
        pool = new SlotPool(2);
        subscriberThreads = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void stopRelay() throws InterruptedException {
        subscriberThreads.shutdownNow();
        assertThat(subscriberThreads.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void frameReturnsItsSlotOnlyAfterEverySubscriberReleasedIt() throws Exception {
        Relay relay = new Relay(subscriberThreads);
        Frame frame = new Frame(pool, "keyframe-1");
        assertThat(frame.refCnt()).isEqualTo(1);
        assertThat(pool.available()).isEqualTo(1);

        List<String> received = relay.broadcast(frame, List.of("mobile", "desktop", "recorder"));

        assertThat(received).containsExactlyInAnyOrder(
                "mobile:keyframe-1", "desktop:keyframe-1", "recorder:keyframe-1");
        assertThat(frame.refCnt()).isZero();
        assertThat(frame.freed).isTrue();
        assertThat(pool.available()).isEqualTo(2);
        assertThat(pool.returned()).containsExactly(frame.slot);
        assertThat(frame.hints()).hasSize(4).contains("relay:fan-out:3");
        assertThat(frame.hints()).filteredOn(hint -> hint.startsWith("subscriber:")).hasSize(3);
    }

    @Test
    void retainedFrameSurvivesTheReleaseOfItsCreator() {
        Frame frame = new Frame(pool, "delta-7");

        ReferenceCounted archived = ReferenceCountUtil.retain(frame, 2);
        assertThat(archived).isSameAs(frame);
        assertThat(ReferenceCountUtil.refCnt(frame)).isEqualTo(3);

        assertThat(ReferenceCountUtil.release(frame)).isFalse();
        assertThat(frame.freed).isFalse();
        assertThat(frame.payloadText()).isEqualTo("delta-7");
        assertThat(pool.available()).isEqualTo(1);

        assertThat(ReferenceCountUtil.release(frame, 2)).isTrue();
        assertThat(frame.freed).isTrue();
        assertThat(pool.available()).isEqualTo(2);
    }

    @Test
    void helpersPassPlainMessagesThroughUntouched() {
        String heartbeat = "heartbeat";

        assertThat(ReferenceCountUtil.retain(heartbeat)).isSameAs(heartbeat);
        assertThat(ReferenceCountUtil.touch(heartbeat, "relay")).isSameAs(heartbeat);
        assertThat(ReferenceCountUtil.refCnt(heartbeat)).isEqualTo(-1);
        assertThat(ReferenceCountUtil.release(heartbeat)).isFalse();
        ReferenceCountUtil.safeRelease(heartbeat);

        Frame frame = new Frame(pool, "audio-3");
        assertThat(ReferenceCountUtil.touch(frame, "mixer")).isSameAs(frame);
        assertThat(frame.touch()).isSameAs(frame);
        assertThat(frame.hints()).containsExactly("mixer", "touched");
        assertThat(frame.release()).isTrue();
    }

    @Test
    void freedFrameRejectsFurtherRetainAndRelease() {
        Frame frame = new Frame(pool, "keyframe-2");
        assertThat(frame.release()).isTrue();
        assertThat(frame.refCnt()).isZero();

        assertThatThrownBy(frame::retain)
                .isInstanceOf(IllegalReferenceCountException.class)
                .hasMessageContaining("refCnt: 0");
        assertThatThrownBy(frame::release)
                .isInstanceOf(IllegalReferenceCountException.class)
                .hasMessageContaining("refCnt: 0");
        assertThatThrownBy(() -> ReferenceCountUtil.release(frame))
                .isInstanceOf(IllegalReferenceCountException.class);

        ReferenceCountUtil.safeRelease(frame);
        assertThat(pool.returned()).containsExactly(frame.slot);
        assertThat(pool.available()).isEqualTo(2);
    }

    @Test
    void releasingMoreReferencesThanHeldIsRejectedWithoutFreeing() {
        Frame frame = new Frame(pool, "keyframe-3");
        frame.retain();

        assertThatThrownBy(() -> frame.release(3))
                .isInstanceOf(IllegalReferenceCountException.class)
                .hasMessageContaining("refCnt: 2");
        assertThat(frame.refCnt()).isEqualTo(2);
        assertThat(frame.freed).isFalse();

        assertThatThrownBy(() -> frame.retain(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(frame.release(2)).isTrue();
        assertThat(pool.available()).isEqualTo(2);
    }

    /** Fans a frame out to subscribers on worker threads, one retained reference each. */
    private static final class Relay {

        private final ExecutorService workers;

        Relay(ExecutorService workers) {
            this.workers = workers;
        }

        List<String> broadcast(Frame frame, List<String> subscribers) throws Exception {
            ReferenceCountUtil.touch(frame, "relay:fan-out:" + subscribers.size());
            ReferenceCountUtil.retain(frame, subscribers.size());
            List<Future<String>> deliveries = new ArrayList<>();
            try {
                for (String subscriber : subscribers) {
                    deliveries.add(workers.submit(deliver(frame, subscriber)));
                }
            } finally {
                frame.release();
            }
            List<String> received = new ArrayList<>();
            for (Future<String> delivery : deliveries) {
                received.add(delivery.get(WAIT_SECONDS, TimeUnit.SECONDS));
            }
            return received;
        }

        private static Callable<String> deliver(Frame frame, String subscriber) {
            return () -> {
                try {
                    ReferenceCountUtil.touch(frame, "subscriber:" + subscriber);
                    return subscriber + ":" + frame.payloadText();
                } finally {
                    ReferenceCountUtil.release(frame);
                }
            };
        }
    }

    /** A fixed set of payload slots that frames borrow and give back on deallocation. */
    private static final class SlotPool {

        private final Deque<byte[]> free = new ArrayDeque<>();

        private final List<byte[]> returned = new ArrayList<>();

        SlotPool(int size) {
            for (int i = 0; i < size; i++) {
                free.push(new byte[64]);
            }
        }

        synchronized byte[] borrow() {
            assertThat(free).isNotEmpty();
            return free.pop();
        }

        synchronized void giveBack(byte[] slot) {
            returned.add(slot);
            free.push(slot);
        }

        synchronized int available() {
            return free.size();
        }

        synchronized List<byte[]> returned() {
            return new ArrayList<>(returned);
        }
    }

    /** A video frame whose payload lives in a pooled slot until its last reference is released. */
    private static final class Frame extends AbstractReferenceCounted {

        private final SlotPool pool;

        private final byte[] slot;

        private final int length;

        private final List<String> hints = Collections.synchronizedList(new ArrayList<>());

        private volatile boolean freed;

        Frame(SlotPool pool, String payload) {
            this.pool = pool;
            this.slot = pool.borrow();
            byte[] bytes = payload.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(bytes, 0, slot, 0, bytes.length);
            this.length = bytes.length;
        }

        String payloadText() {
            assertThat(freed).isFalse();
            return new String(slot, 0, length, StandardCharsets.US_ASCII);
        }

        List<String> hints() {
            synchronized (hints) {
                return new ArrayList<>(hints);
            }
        }

        @Override
        public Frame touch(Object hint) {
            hints.add(String.valueOf(hint));
            return this;
        }

        @Override
        public Frame touch() {
            return touch("touched");
        }

        @Override
        protected void deallocate() {
            freed = true;
            pool.giveBack(slot);
        }
    }
}
