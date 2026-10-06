/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_openfeign_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.util.ConstructorUtils;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

public class ConstructorUtilsTest {

    @Test
    void resolvesCompatiblePublicConstructorAndItsParameters() throws NoSuchMethodException {
        Class<?>[] requestedTypes = {String.class, Integer.class};

        Class<?>[] parameterTypes = ConstructorUtils.getConstructorParameters(Pair.class, requestedTypes);
        Constructor<Pair> constructor = ConstructorUtils.getConstructor(Pair.class, parameterTypes);

        assertThat(parameterTypes).containsExactly(String.class, Integer.class);
        assertThat(constructor.getDeclaringClass()).isEqualTo(Pair.class);
        assertThat(constructor.getParameterTypes()).containsExactly(parameterTypes);
    }

    public static final class Pair {

        private final String name;
        private final Integer value;

        public Pair(String name, Integer value) {
            this.name = name;
            this.value = value;
        }

        public String name() {
            return name;
        }

        public Integer value() {
            return value;
        }
    }
}
