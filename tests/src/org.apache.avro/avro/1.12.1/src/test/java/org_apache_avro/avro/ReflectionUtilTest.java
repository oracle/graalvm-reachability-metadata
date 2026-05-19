/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro;

import java.util.function.Function;

import org.apache.avro.reflect.ReflectionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilTest {
    @Test
    void createsFunctionBackedByPublicOneArgumentConstructor() {
        Function<String, ConstructedFromString> constructor = ReflectionUtil.getConstructorAsFunction(String.class,
                ConstructedFromString.class);

        ConstructedFromString constructed = constructor.apply("created by method handle");

        assertThat(constructed.value).isEqualTo("created by method handle");
    }

    @Test
    void returnsNullWhenOneArgumentConstructorIsNotAccessible() {
        Function<String, PrivateStringConstructor> constructor = ReflectionUtil.getConstructorAsFunction(String.class,
                PrivateStringConstructor.class);

        assertThat(constructor).isNull();
    }

    public static class ConstructedFromString {
        private final String value;

        public ConstructedFromString(String value) {
            this.value = value;
        }
    }

    public static class PrivateStringConstructor {
        private PrivateStringConstructor(String value) {
        }
    }
}
