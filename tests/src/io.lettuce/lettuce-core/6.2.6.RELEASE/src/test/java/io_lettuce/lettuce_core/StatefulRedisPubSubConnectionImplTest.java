/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisChannelWriter;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.protocol.ConnectionFacade;
import io.lettuce.core.protocol.RedisCommand;
import io.lettuce.core.pubsub.PubSubEndpoint;
import io.lettuce.core.pubsub.RedisPubSubReactiveCommandsImpl;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnectionImpl;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import io.lettuce.core.resource.ClientResources;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class StatefulRedisPubSubConnectionImplTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void resubscribeConvertsRememberedChannelsAndPatternsToTypedArrays() throws Exception {
        RecordingRedisChannelWriter writer = new RecordingRedisChannelWriter();
        TestPubSubConnection connection = null;
        try {
            SubscribedPubSubEndpoint endpoint = new SubscribedPubSubEndpoint(writer.getClientResources());
            connection = new TestPubSubConnection(endpoint, writer);

            List<RedisFuture<Void>> futures = connection.resubscribeNow();

            assertThat(futures).hasSize(2);
            assertThat(writer.commandTypes()).containsExactly("SUBSCRIBE", "PSUBSCRIBE");
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } finally {
                writer.shutdownClientResources();
            }
        }
    }

    private static final class TestPubSubConnection extends StatefulRedisPubSubConnectionImpl<String, String> {
        private TestPubSubConnection(SubscribedPubSubEndpoint endpoint, RecordingRedisChannelWriter writer) {
            super(endpoint, writer, StringCodec.UTF8, COMMAND_TIMEOUT);
        }

        private List<RedisFuture<Void>> resubscribeNow() {
            return resubscribe();
        }

        @Override
        protected RedisPubSubCommands<String, String> newRedisSyncCommandsImpl() {
            return null;
        }

        @Override
        protected RedisPubSubReactiveCommandsImpl<String, String> newRedisReactiveCommandsImpl() {
            return null;
        }
    }

    private static final class SubscribedPubSubEndpoint extends PubSubEndpoint<String, String> {
        private final Set<String> channels = new LinkedHashSet<>(List.of("orders", "invoices"));
        private final Set<String> patterns = new LinkedHashSet<>(List.of("tenant:*"));

        private SubscribedPubSubEndpoint(ClientResources clientResources) {
            super(ClientOptions.create(), clientResources);
        }

        @Override
        public boolean hasChannelSubscriptions() {
            return true;
        }

        @Override
        public Set<String> getChannels() {
            return channels;
        }

        @Override
        public boolean hasPatternSubscriptions() {
            return true;
        }

        @Override
        public Set<String> getPatterns() {
            return patterns;
        }
    }

    private static final class RecordingRedisChannelWriter implements RedisChannelWriter {
        private final ClientResources clientResources = ClientResources.create();
        private final List<RedisCommand<?, ?, ?>> writtenCommands = new CopyOnWriteArrayList<>();

        @Override
        public <K, V, T> RedisCommand<K, V, T> write(RedisCommand<K, V, T> command) {
            writtenCommands.add(command);
            return command;
        }

        @Override
        public <K, V> Collection<RedisCommand<K, V, ?>> write(Collection<? extends RedisCommand<K, V, ?>> commands) {
            List<RedisCommand<K, V, ?>> written = new ArrayList<>(commands.size());
            for (RedisCommand<K, V, ?> command : commands) {
                writtenCommands.add(command);
                written.add(command);
            }
            return written;
        }

        @Override
        public void close() {
        }

        @Override
        public CompletableFuture<Void> closeAsync() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void reset() {
        }

        @Override
        public void setConnectionFacade(ConnectionFacade connection) {
        }

        @Override
        public void setAutoFlushCommands(boolean autoFlush) {
        }

        @Override
        public void flushCommands() {
        }

        @Override
        public ClientResources getClientResources() {
            return clientResources;
        }

        private List<String> commandTypes() {
            return writtenCommands.stream()
                    .map(command -> command.getType().name())
                    .toList();
        }

        private void shutdownClientResources() throws InterruptedException {
            clientResources.shutdown(0, 2, TimeUnit.SECONDS).await(5, TimeUnit.SECONDS);
        }
    }
}
