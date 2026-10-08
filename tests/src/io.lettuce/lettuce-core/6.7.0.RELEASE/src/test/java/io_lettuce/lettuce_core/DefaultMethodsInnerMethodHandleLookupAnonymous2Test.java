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

public class DefaultMethodsInnerMethodHandleLookupAnonymous2Test {
    interface DefaultCommands extends Commands {
        @Command("SET ?0 ?1")
        String write(String key, String value);

        @Command("GET ?0")
        String read(String key);

        default String writeThenRead(String key, String value) {
            write(key, value);
            return read(key);
        }
    }

    @Test
    void invokesDefaultCommandInterfaceMethod() throws Exception {
        try (Lettuce_coreTest.FakeRedisServer server = new Lettuce_coreTest.FakeRedisServer()) {
            RedisClient client = LettuceTestSupport.createClient(server);
            try (StatefulRedisConnection<String, String> connection = LettuceTestSupport.connect(client)) {
                DefaultCommands commands = new RedisCommandFactory(connection).getCommands(DefaultCommands.class);

                assertThat(commands.writeThenRead("default-key", "default-value")).isEqualTo("default-value");
            } finally {
                LettuceTestSupport.shutdown(client);
            }
        }
    }
}
