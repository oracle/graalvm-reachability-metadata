/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.dynamic.Commands;
import io.lettuce.core.dynamic.RedisCommandFactory;
import io.lettuce.core.dynamic.annotation.Command;
import org.junit.jupiter.api.Test;

public class DefaultRedisCommandsMetadataTest {
    interface ReadCommands extends Commands {
        @Command("GET ?0")
        String read(String key);
    }

    interface InheritedReadCommands extends ReadCommands {
    }

    @Test
    void discoversAndExecutesInheritedCommandMethods() throws Exception {
        try (Lettuce_coreTest.FakeRedisServer server = new Lettuce_coreTest.FakeRedisServer()) {
            RedisClient client = LettuceTestSupport.createClient(server);
            try (StatefulRedisConnection<String, String> connection = LettuceTestSupport.connect(client)) {
                connection.sync().set("metadata-key", "metadata-value");

                InheritedReadCommands commands = new RedisCommandFactory(connection)
                        .getCommands(InheritedReadCommands.class);

                assertThat(commands.read("metadata-key")).isEqualTo("metadata-value");
            } finally {
                LettuceTestSupport.shutdown(client);
            }
        }
    }
}
