/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.util.AsciiString;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NettyTests {

    @Test
    void asciiStringSupportsCaseInsensitiveOperationsAndParsing() {
        AsciiString value = new AsciiString("Netty-42");

        assertThat(value.contentEqualsIgnoreCase("netty-42")).isTrue();
        assertThat(value.subSequence(0, 5).toString()).isEqualTo("Netty");
        assertThat(AsciiString.indexOfIgnoreCase(value, "TTY", 0)).isEqualTo(2);
        assertThat(new AsciiString("42").parseInt()).isEqualTo(42);
    }

    @Test
    void defaultAttributeMapStoresAndRemovesValuesByKey() {
        DefaultAttributeMap attributeMap = new DefaultAttributeMap();
        AttributeKey<String> protocolKey = AttributeKey.valueOf(NettyTests.class, "protocol");
        Attribute<String> protocol = attributeMap.attr(protocolKey);

        assertThat(protocol.setIfAbsent("http/2")).isNull();
        assertThat(attributeMap.hasAttr(protocolKey)).isTrue();
        assertThat(attributeMap.attr(protocolKey).get()).isEqualTo("http/2");
        assertThat(protocol.getAndRemove()).isEqualTo("http/2");
        assertThat(attributeMap.hasAttr(protocolKey)).isFalse();
    }

    @Test
    void defaultPromiseCompletesSynchronouslyAndNotifiesListeners() throws Exception {
        DefaultPromise<String> promise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
        AtomicReference<String> observedValue = new AtomicReference<>();
        CountDownLatch completion = new CountDownLatch(1);

        promise.addListener(future -> {
            observedValue.set((String) future.getNow());
            completion.countDown();
        });

        assertThat(promise.trySuccess("ready")).isTrue();
        assertThat(completion.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(promise.syncUninterruptibly().getNow()).isEqualTo("ready");
        assertThat(observedValue.get()).isEqualTo("ready");
    }

    @Test
    void fastThreadLocalKeepsStateScopedToTheWorkerThread() throws Exception {
        FastThreadLocal<String> localValue = new FastThreadLocal<>();
        AtomicReference<String> observedValue = new AtomicReference<>();
        FastThreadLocalThread worker = new FastThreadLocalThread(() -> {
            localValue.set("worker");
            observedValue.set(localValue.get());
            localValue.remove();
            FastThreadLocal.removeAll();
        });

        try {
            worker.start();
            worker.join();

            assertThat(observedValue.get()).isEqualTo("worker");
            assertThat(localValue.getIfExists()).isNull();
        } finally {
            FastThreadLocal.removeAll();
        }
    }

    @Test
    void hashedWheelTimerExecutesScheduledTasks() throws Exception {
        HashedWheelTimer timer = new HashedWheelTimer(10, TimeUnit.MILLISECONDS);
        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<Timeout> executedTimeout = new AtomicReference<>();

        try {
            timer.newTimeout(timeout -> {
                executedTimeout.set(timeout);
                completion.countDown();
            }, 25, TimeUnit.MILLISECONDS);

            assertThat(completion.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executedTimeout.get()).isNotNull();
            assertThat(executedTimeout.get().isExpired()).isTrue();
            assertThat(timer.pendingTimeouts()).isZero();
        } finally {
            timer.stop();
        }
    }

    @Test
    void internalLoggerFactoryReturnsANamedLogger() {
        InternalLogger logger = InternalLoggerFactory.getInstance(NettyTests.class);

        logger.debug("netty-common logger initialized");

        assertThat(logger.name()).endsWith("NettyTests");
    }
}
