/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.util.Attribute;
import io.netty.util.CharsetUtil;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.HashedWheelTimer;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.Signal;
import io.netty.util.Timeout;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NettyTests {
    @Test
    public void storesAttributesAndUsesCommonConstants() {
        AttributeKey<String> key = AttributeKey.valueOf(NettyTests.class, "message");
        DefaultAttributeMap attributeMap = new DefaultAttributeMap();

        Attribute<String> attribute = attributeMap.attr(key);
        assertThat(attribute.setIfAbsent("alpha")).isNull();
        assertThat(attribute.get()).isEqualTo("alpha");
        assertThat(attribute.getAndSet("beta")).isEqualTo("alpha");
        assertThat(attribute.getAndRemove()).isEqualTo("beta");

        assertThat(CharsetUtil.UTF_8.name()).isEqualTo("UTF-8");
        Signal signal = Signal.valueOf(NettyTests.class, "COMMON_SIGNAL");
        assertThat(signal.name()).endsWith("COMMON_SIGNAL");
        signal.expect(signal);
    }

    @Test
    public void completesPromisesAndCreatesNamedThreads() throws InterruptedException {
        CountDownLatch threadRan = new CountDownLatch(1);
        DefaultThreadFactory threadFactory = new DefaultThreadFactory("netty-common-test", true);
        Thread thread = threadFactory.newThread(threadRan::countDown);

        thread.start();
        assertThat(threadRan.await(10, TimeUnit.SECONDS)).isTrue();
        thread.join(TimeUnit.SECONDS.toMillis(10));
        assertThat(thread.getName()).startsWith("netty-common-test-");
        assertThat(thread.isDaemon()).isTrue();

        DefaultPromise<String> promise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
        AtomicReference<String> completedValue = new AtomicReference<>();
        promise.addListener(future -> completedValue.set((String) future.getNow()));

        assertThat(promise.setSuccess("done")).isSameAs(promise);
        assertThat(promise.isSuccess()).isTrue();
        assertThat(completedValue).hasValue("done");
    }

    @Test
    public void schedulesTimeoutsAndCreatesLeakDetectors() throws InterruptedException {
        HashedWheelTimer timer = new HashedWheelTimer(100, TimeUnit.MILLISECONDS);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Timeout> observedTimeout = new AtomicReference<>();

        try {
            Timeout timeout = timer.newTimeout(t -> {
                observedTimeout.set(t);
                latch.countDown();
            }, 100, TimeUnit.MILLISECONDS);

            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(timeout.isExpired()).isTrue();
            assertThat(observedTimeout).hasValue(timeout);

            ResourceLeakDetector<String> leakDetector = new ResourceLeakDetector<>(String.class);
            assertThat(leakDetector).isNotNull();
        } finally {
            Set<Timeout> unprocessedTimeouts = timer.stop();
            assertThat(unprocessedTimeouts).isEmpty();
        }
    }
}
