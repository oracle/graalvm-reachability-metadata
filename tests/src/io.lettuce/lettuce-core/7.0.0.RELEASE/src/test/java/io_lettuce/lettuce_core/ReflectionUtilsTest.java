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
import io.lettuce.core.dynamic.annotation.Param;
import io.lettuce.core.dynamic.support.ReflectionUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {
    interface NamedCommands extends Commands {
        @Command("SET :key :value")
        String store(@Param("key") String key, @Param("value") String value);
    }

    public interface DefaultNamedCommands {
        default String label() {
            return "commands";
        }
    }

    public static final class CommandFixture implements DefaultNamedCommands {
        public final String name = "fixture";

        public String command() {
            return "PING";
        }
    }

    @Test
    void inspectsMethodsAndFieldsThroughReflectionUtilities() throws Exception {
        Set<String> methods = new LinkedHashSet<>();
        ReflectionUtils.doWithMethods(CommandFixture.class, method -> methods.add(method.getName()));
        Set<String> fields = new LinkedHashSet<>();
        ReflectionUtils.doWithFields(CommandFixture.class, field -> fields.add(field.getName()));
        Method label = ReflectionUtils.findMethod(CommandFixture.class, "label");
        Field name = CommandFixture.class.getField("name");

        assertThat(methods).contains("command", "label");
        assertThat(fields).contains("name");
        assertThat(ReflectionUtils.invokeMethod(label, new CommandFixture())).isEqualTo("commands");
        assertThat(ReflectionUtils.getField(name, new CommandFixture())).isEqualTo("fixture");
    }

    @Test
    void resolvesAnnotatedCommandParameters() throws Exception {
        try (Lettuce_coreTest.FakeRedisServer server = new Lettuce_coreTest.FakeRedisServer()) {
            RedisClient client = LettuceTestSupport.createClient(server);
            try (StatefulRedisConnection<String, String> connection = LettuceTestSupport.connect(client)) {
                NamedCommands commands = new RedisCommandFactory(connection).getCommands(NamedCommands.class);

                assertThat(commands.store("named-key", "named-value")).isEqualTo("OK");
                assertThat(connection.sync().get("named-key")).isEqualTo("named-value");
            } finally {
                LettuceTestSupport.shutdown(client);
            }
        }
    }
}
