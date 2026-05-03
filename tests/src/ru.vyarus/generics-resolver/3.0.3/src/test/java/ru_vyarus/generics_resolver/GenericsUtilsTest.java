/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ru_vyarus.generics_resolver;

import org.junit.jupiter.api.Test;
import ru.vyarus.java.generics.resolver.context.container.GenericArrayTypeImpl;
import ru.vyarus.java.generics.resolver.util.GenericsUtils;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericsUtilsTest {

    private static final Map<String, Type> NO_GENERICS = Collections.emptyMap();

    @Test
    void resolvesGenericArrayComponentClassToArrayClass() {
        GenericArrayType type = new GenericArrayTypeImpl(String.class);

        Class<?> resolved = GenericsUtils.resolveClass(type, NO_GENERICS);

        assertThat(resolved).isEqualTo(String[].class);
    }

    @Test
    void resolvesGenericArrayComponentArrayClassToNestedArrayClass() {
        GenericArrayType type = new GenericArrayTypeImpl(String[].class);

        Class<?> resolved = GenericsUtils.resolveClass(type, NO_GENERICS);

        assertThat(resolved).isEqualTo(String[][].class);
    }
}
