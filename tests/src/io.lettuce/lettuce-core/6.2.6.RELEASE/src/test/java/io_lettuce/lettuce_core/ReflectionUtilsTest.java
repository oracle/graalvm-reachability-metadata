/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.dynamic.support.ReflectionUtils;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {

    @Test
    void methodLookupIncludesInterfaceMethodsAndDefaultMethods() {
        ReflectiveFixture fixture = new ReflectiveFixture();

        Method interfaceMethod = ReflectionUtils.findMethod(GreetingOperations.class, "namedGreeting", String.class);
        Method defaultMethod = ReflectionUtils.findMethod(ReflectiveFixture.class, "defaultGreeting");

        assertThat(interfaceMethod).isNotNull();
        assertThat(defaultMethod).isNotNull();
        assertThat(ReflectionUtils.invokeMethod(interfaceMethod, fixture, "Ada")).isEqualTo("Hello Ada");
        assertThat(ReflectionUtils.invokeMethod(defaultMethod, fixture)).isEqualTo("Hello default");
    }

    @Test
    void fieldCallbacksCanReadFieldsFromTheClassHierarchy() {
        ReflectiveFixture fixture = new ReflectiveFixture();
        Map<String, Object> values = new LinkedHashMap<>();

        ReflectionUtils.doWithFields(ReflectiveFixture.class, field -> values.put(field.getName(),
                ReflectionUtils.getField(field, fixture)), field -> !field.isSynthetic());

        assertThat(values)
                .containsEntry("message", "Hello")
                .containsEntry("suffix", "!");
    }

    public interface GreetingOperations {

        default String defaultGreeting() {
            return "Hello default";
        }

        String namedGreeting(String name);
    }

    public static class BaseFixture {
        public final String suffix = "!";
    }

    public static final class ReflectiveFixture extends BaseFixture implements GreetingOperations {
        public final String message = "Hello";

        @Override
        public String namedGreeting(String name) {
            return message + " " + name;
        }
    }
}
