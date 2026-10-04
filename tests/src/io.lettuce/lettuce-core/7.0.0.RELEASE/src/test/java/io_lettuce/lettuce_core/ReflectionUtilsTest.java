/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.dynamic.support.ReflectionUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {
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
}
