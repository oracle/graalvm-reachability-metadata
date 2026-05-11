/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.reflect.Invokable;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

public class InvokableInnerConstructorInvokableTest {
    @Test
    void invokeCreatesInstanceThroughWrappedConstructor() throws Exception {
        Constructor<ConstructedValue> constructor =
                ConstructedValue.class.getConstructor(String.class, int.class);
        Invokable<ConstructedValue, ConstructedValue> invokable = Invokable.from(constructor);

        ConstructedValue value = invokable.invoke(null, "guava", 33);

        assertThat(value.name()).isEqualTo("guava");
        assertThat(value.version()).isEqualTo(33);
        assertThat(invokable.getReturnType().getRawType()).isEqualTo(ConstructedValue.class);
        assertThat(invokable.isOverridable()).isFalse();
    }

    public static final class ConstructedValue {
        private final String name;
        private final int version;

        public ConstructedValue(String name, int version) {
            this.name = name;
            this.version = version;
        }

        String name() {
            return name;
        }

        int version() {
            return version;
        }
    }
}
