/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.dynamic.support.ClassTypeInformation;
import io.lettuce.core.dynamic.support.TypeInformation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class GenericArrayTypeInformationTest {
    interface ArrayResult<T> {
        T[] values();
    }

    @Test
    void resolvesGenericArrayReturnType() throws Exception {
        Method values = ArrayResult.class.getMethod("values");
        TypeInformation<?> returnType = ClassTypeInformation.fromReturnTypeOf(values);

        assertThat(returnType.getType()).isEqualTo(Object[].class);
        assertThat(returnType.getComponentType().getType()).isEqualTo(Object.class);
    }
}
