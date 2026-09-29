/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.protocol.ProtocolVersion;
import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Hooks;

public class OperatorsTest {
    private static final String HOOK_KEY = OperatorsTest.class.getName();

    @Test
    void reactiveCommandMapsQueueOverflowThroughReactorHook() throws Exception {
        try (Lettuce_coreTest.FakeRedisServer server = new Lettuce_coreTest.FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            client.setOptions(ClientOptions.builder()
                    .protocolVersion(ProtocolVersion.RESP2)
                    .build());
            StatefulRedisConnection<String, String> connection = null;
            try {
                connection = client.connect();
                connection.setTimeout(LettuceTestSupport.TIMEOUT);
                connection.sync().set("first", "one");
                connection.sync().set("second", "two");
                RedisReactiveCommands<String, String> reactive = connection.reactive();

                AtomicBoolean queueWrapped = new AtomicBoolean();
                AtomicBoolean queueFull = new AtomicBoolean();
                AtomicReference<Throwable> hookError = new AtomicReference<>();
                AtomicReference<Object> hookValue = new AtomicReference<>();
                CountDownLatch hookInvoked = new CountDownLatch(1);
                Hooks.addQueueWrapper(HOOK_KEY, queue -> queueWrapped.compareAndSet(false, true)
                        ? new RejectingQueue<>(queue, queueFull)
                        : queue);
                Hooks.onOperatorError(HOOK_KEY, (error, value) -> {
                    hookError.set(error);
                    hookValue.set(value);
                    hookInvoked.countDown();
                    return new IllegalStateException("mapped reactive overflow", error);
                });
                try {
                    RecordingSubscriber subscriber = new RecordingSubscriber(queueFull);
                    reactive.mget("first", "second").subscribe(subscriber);

                    assertThat(hookInvoked.await(LettuceTestSupport.TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
                    assertThat(queueWrapped).isTrue();
                    assertThat(subscriber.values).extracting(KeyValue::getValue).containsExactly("one");
                    assertThat(hookError.get())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("overrun");
                    assertThat(hookValue.get()).isEqualTo(KeyValue.just("second", "two"));
                } finally {
                    Hooks.resetOnOperatorError(HOOK_KEY);
                    Hooks.removeQueueWrapper(HOOK_KEY);
                }
            } finally {
                if (connection != null) {
                    connection.close();
                }
                LettuceTestSupport.shutdown(client);
            }
        }
    }

    private static final class RecordingSubscriber implements Subscriber<KeyValue<String, String>> {
        private final AtomicBoolean queueFull;
        private final List<KeyValue<String, String>> values = new CopyOnWriteArrayList<>();

        private RecordingSubscriber(AtomicBoolean queueFull) {
            this.queueFull = queueFull;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(KeyValue<String, String> value) {
            values.add(value);
            queueFull.set(true);
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }
    }

    private static final class RejectingQueue<E> extends AbstractQueue<E> {
        private final Queue<E> delegate;
        private final AtomicBoolean full;

        private RejectingQueue(Queue<E> delegate, AtomicBoolean full) {
            this.delegate = delegate;
            this.full = full;
        }

        @Override
        public boolean offer(E value) {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return !full.get() && delegate.isEmpty();
        }

        @Override
        public E poll() {
            return delegate.poll();
        }

        @Override
        public E peek() {
            return delegate.peek();
        }

        @Override
        public Iterator<E> iterator() {
            return delegate.iterator();
        }

        @Override
        public int size() {
            return delegate.size();
        }
    }
}
