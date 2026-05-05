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

    @Test
    void getTypeCreatesArrayClassForResolvedGenericArrayReturnType() throws Exception {
        Method method = GenericArrayRepository.class.getMethod("findAll");
        TypeInformation<?> repositoryType = ClassTypeInformation.from(StringArrayRepository.class)
                .getSuperTypeInformation(GenericArrayRepository.class);

        TypeInformation<?> returnType = repositoryType.getReturnType(method);

        assertThat(returnType.getType()).isEqualTo(String[].class);
        assertThat(returnType.getComponentType().getType()).isEqualTo(String.class);
    }

    public interface GenericArrayRepository<T extends CharSequence> {

        T[] findAll();
    }

    public static final class StringArrayRepository implements GenericArrayRepository<String> {

        @Override
        public String[] findAll() {
            return new String[] {"lettuce" };
        }
    }
}
