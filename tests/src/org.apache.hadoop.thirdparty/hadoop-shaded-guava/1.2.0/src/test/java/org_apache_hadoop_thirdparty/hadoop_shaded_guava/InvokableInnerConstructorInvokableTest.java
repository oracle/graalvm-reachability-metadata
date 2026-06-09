/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.apache.hadoop.thirdparty.com.google.common.reflect.Invokable;
import org.junit.jupiter.api.Test;

public class InvokableInnerConstructorInvokableTest {

    @Test
    void constructorInvokableCreatesInstanceThroughPublicConstructor()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Constructor<ConstructedValue> constructor = ConstructedValue.class.getConstructor(String.class, int.class);
        Invokable<ConstructedValue, ConstructedValue> invokable = Invokable.from(constructor);

        ConstructedValue value = invokable.invoke(null, "hadoop", 3);

        assertThat(value.getName()).isEqualTo("hadoop");
        assertThat(value.getCount()).isEqualTo(3);
        assertThat(invokable.getReturnType().getRawType()).isEqualTo(ConstructedValue.class);
    }

    public static final class ConstructedValue {
        private final String name;
        private final int count;

        public ConstructedValue(String name, int count) {
            this.name = name;
            this.count = count;
        }

        String getName() {
            return name;
        }

        int getCount() {
            return count;
        }
    }
}
