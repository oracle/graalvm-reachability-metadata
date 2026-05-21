/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_http_client.google_http_client;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.GenericData;
import com.google.api.client.util.Types;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TypesTest {

    @Test
    public void newInstanceCreatesClassWithPublicDefaultConstructor() {
        GenericData instance = Types.newInstance(GenericData.class);

        assertThat(instance).isInstanceOf(GenericData.class);
    }

    @Test
    public void newInstanceExplainsAbstractClass() {
        assertThatThrownBy(() -> Types.newInstance(JsonFactory.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("because it is abstract");
    }

    @Test
    public void getRawArrayComponentTypeResolvesGenericArrayType() {
        Type genericArrayType = new SimpleGenericArrayType(GenericData.class);

        Class<?> rawComponentType = Types.getRawArrayComponentType(
                Collections.emptyList(), genericArrayType);

        assertThat(rawComponentType).isEqualTo(GenericData[].class);
    }

    @Test
    public void toArrayCreatesPrimitiveArray() {
        Object array = Types.toArray(Arrays.asList(1, 2, 3), int.class);

        assertThat(array).isInstanceOf(int[].class);
        assertThat((int[]) array).containsExactly(1, 2, 3);
    }

    @Test
    public void toArrayCreatesReferenceArray() {
        Object array = Types.toArray(Arrays.asList("alpha", "beta"), String.class);

        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).containsExactly("alpha", "beta");
    }

    private static final class SimpleGenericArrayType implements GenericArrayType {
        private final Type componentType;

        private SimpleGenericArrayType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }
}
