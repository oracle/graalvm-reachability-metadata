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
import java.util.List;
import org.junit.jupiter.api.Test;

public class GenericArrayTypeInformationTest {

    @Test
    void resolvesGenericArrayReturnTypeToArrayClass() throws NoSuchMethodException {
        Method method = GenericArrayFixture.class.getMethod("findLists");

        TypeInformation<?> typeInformation = ClassTypeInformation.fromReturnTypeOf(method);

        assertThat(typeInformation.getType()).isEqualTo(List[].class);
        assertThat(typeInformation.isCollectionLike()).isTrue();
        assertThat(typeInformation.getComponentType().getType()).isEqualTo(List.class);
        assertThat(typeInformation.getComponentType().getActualType().getType()).isEqualTo(String.class);
    }

    public interface GenericArrayFixture {

        List<String>[] findLists();
    }
}
