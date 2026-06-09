/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.DefaultArraySerializers.ObjectArraySerializer;
import org.junit.jupiter.api.Test;

public class DefaultArraySerializersInnerObjectArraySerializerTest {
    @Test
    void copiesObjectArraysThroughDefaultArraySerializer() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setAsmEnabled(false);

        ArrayElement[] original = new ArrayElement[] {
            new ArrayElement("alpha", 1), null, new ArrayElement("beta", 2)
        };

        ArrayElement[] copied = kryo.copy(original);

        assertThat(kryo.getSerializer(ArrayElement[].class)).isInstanceOf(ObjectArraySerializer.class);
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.getClass()).isSameAs(ArrayElement[].class);
        assertThat(copied[0]).isNotSameAs(original[0]);
        assertThat(copied[0].getName()).isEqualTo(original[0].getName());
        assertThat(copied[0].getValue()).isEqualTo(original[0].getValue());
        assertThat(copied[1]).isNull();
        assertThat(copied[2]).isNotSameAs(original[2]);
        assertThat(copied[2].getName()).isEqualTo(original[2].getName());
        assertThat(copied[2].getValue()).isEqualTo(original[2].getValue());
    }

    public static class ArrayElement {
        private String name;
        private int value;

        public ArrayElement() {
        }

        ArrayElement(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }
}
