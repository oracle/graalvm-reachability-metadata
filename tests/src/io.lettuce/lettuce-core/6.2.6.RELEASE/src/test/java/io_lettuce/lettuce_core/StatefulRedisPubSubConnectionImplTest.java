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
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.ConnectionFacade;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.lettuce.core.protocol.RedisCommand;
import io.lettuce.core.pubsub.PubSubEndpoint;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnectionImpl;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import java.time.Duration;
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
    void activatedResubscribesExistingChannelSubscriptions() {
        DefaultClientResources clientResources = DefaultClientResources.create();
        RecordingRedisChannelWriter writer = new RecordingRedisChannelWriter(clientResources);
        SubscribedPubSubEndpoint endpoint = new SubscribedPubSubEndpoint(clientResources, "events");
        StatefulRedisPubSubConnectionImpl<String, String> connection = new StatefulRedisPubSubConnectionImpl<>(endpoint,
                writer, StringCodec.UTF8, COMMAND_TIMEOUT);
        try {
            connection.activated();

            assertThat(writer.commandTypes()).containsExactly(CommandType.SUBSCRIBE);
        } finally {
            connection.close();
            clientResources.shutdown(0, 2, TimeUnit.SECONDS);
        }
    }

    private static final class SubscribedPubSubEndpoint extends PubSubEndpoint<String, String> {
        private final Set<String> channels;

        SubscribedPubSubEndpoint(ClientResources clientResources, String channel) {
            super(ClientOptions.create(), clientResources);
            this.channels = new LinkedHashSet<>();
            this.channels.add(channel);
        }

        @Override
        public boolean hasChannelSubscriptions() {
            return true;
        }

        @Override
        public Set<String> getChannels() {
            return channels;
        }
    }

    private static final class RecordingRedisChannelWriter implements RedisChannelWriter {
        private final ClientResources clientResources;
        private final List<ProtocolKeyword> commandTypes = new CopyOnWriteArrayList<>();

        RecordingRedisChannelWriter(ClientResources clientResources) {
            this.clientResources = clientResources;
        }

        List<ProtocolKeyword> commandTypes() {
            return commandTypes;
        }

        @Override
        public <K, V, T> RedisCommand<K, V, T> write(RedisCommand<K, V, T> command) {
            commandTypes.add(command.getType());
            return command;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <K, V> Collection<RedisCommand<K, V, ?>> write(Collection<? extends RedisCommand<K, V, ?>> commands) {
            for (RedisCommand<K, V, ?> command : commands) {
                commandTypes.add(command.getType());
            }
            return (Collection<RedisCommand<K, V, ?>>) commands;
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
    }
}
