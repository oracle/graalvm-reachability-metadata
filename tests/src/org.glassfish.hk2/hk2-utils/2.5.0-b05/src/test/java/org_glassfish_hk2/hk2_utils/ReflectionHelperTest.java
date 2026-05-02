/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionHelperTest {
    @Test
    public void resolvesRawClassForParameterizedGenericArray() throws NoSuchFieldException {
        final Type genericArrayType = ParameterizedArrayContainer.class.getDeclaredField("values").getGenericType();

        assertThat(ReflectionHelper.getRawClass(genericArrayType)).isEqualTo(List[].class);
    }

    @Test
    public void resolvesTypeVariableArrayToConcreteArrayClass() throws NoSuchFieldException {
        final Field field = GenericArrayContainer.class.getDeclaredField("values");

        assertThat(ReflectionHelper.resolveField(StringArrayContainer.class, field)).isEqualTo(String[].class);
    }

    @Test
    public void createsInstancesSetsFieldsAndInvokesMethods() throws Throwable {
        final Constructor<MutableService> constructor = MutableService.class.getConstructor(String.class);
        final MutableService service = (MutableService) ReflectionHelper.makeMe(
                constructor,
                new Object[] {"hk2"},
                true
        );
        assertThat(service.getName()).isEqualTo("hk2");

        final Field nameField = MutableService.class.getDeclaredField("name");
        ReflectionHelper.setField(nameField, service, "glassfish");
        assertThat(service.getName()).isEqualTo("glassfish");

        final Method method = MutableService.class.getMethod("describe", String.class, int.class);
        assertThat(ReflectionHelper.invoke(service, method, new Object[] {"run", 2}, true))
                .isEqualTo("run:glassfish:2");
    }

    private static final class ParameterizedArrayContainer {
        private List<String>[] values;
    }

    private static class GenericArrayContainer<T> {
        private T[] values;
    }

    private static final class StringArrayContainer extends GenericArrayContainer<String> {
    }

    public static final class MutableService {
        private String name;

        public MutableService(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String describe(String prefix, int count) {
            return prefix + ":" + name + ":" + count;
        }
    }
}
