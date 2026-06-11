/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.internal.ReflectionUtils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void invokesDefaultMethodUsingPrivateLookupInPath() throws Throwable {
        Method method = GreetingDefaults.class.getMethod("format", String.class, int.class);

        assertThat(ReflectionUtils.isDefault(method)).isTrue();
        Object methodHandle = ReflectionUtils.getMethodHandle(method);
        Object result = ReflectionUtils.invokeDefaultMethod(
                new GreetingDefaultsImplementation(), methodHandle, "jna", 5);

        assertThat(result).isEqualTo("jna:5");
    }

    @Test
    void invokesJdkDefaultMethodUsingLookupConstructorFallback() throws Throwable {
        Method method = Iterator.class.getMethod("forEachRemaining", Consumer.class);
        Iterator<String> iterator = Collections.singleton("jna").iterator();
        List<String> values = new ArrayList<>();

        Object methodHandle = ReflectionUtils.getMethodHandle(method);
        ReflectionUtils.invokeDefaultMethod(iterator, methodHandle, (Consumer<String>) values::add);

        assertThat(values).containsExactly("jna");
    }

    private interface GreetingDefaults {
        default String format(String text, int count) {
            return text + ":" + count;
        }
    }

    private static final class GreetingDefaultsImplementation implements GreetingDefaults {
    }
}
