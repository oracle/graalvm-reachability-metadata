/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.DefaultSerializers.DateSerializer;
import java.util.Date;
import org.junit.jupiter.api.Test;

public class DefaultSerializersInnerDateSerializerTest {
    @Test
    void readsDateSubclassUsingPublicLongConstructor() {
        Kryo kryo = new Kryo();
        DateSerializer serializer = new DateSerializer();
        CustomDate original = new CustomDate(1_715_713_245_678L);

        Output output = new Output(32, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        Date read = serializer.read(kryo, input, customDateType());

        assertThat(read).isInstanceOf(CustomDate.class);
        assertThat(read.getTime()).isEqualTo(original.getTime());
        assertThat(((CustomDate) read).createdWithLongConstructor).isTrue();
    }

    @Test
    void copiesDateSubclassUsingPublicLongConstructor() {
        Kryo kryo = new Kryo();
        DateSerializer serializer = new DateSerializer();
        CustomDate original = new CustomDate(1_715_713_245_678L);

        Date copy = serializer.copy(kryo, original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy).isInstanceOf(CustomDate.class);
        assertThat(copy.getTime()).isEqualTo(original.getTime());
        assertThat(((CustomDate) copy).createdWithLongConstructor).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static Class<Date> customDateType() {
        return (Class<Date>) (Class<?>) CustomDate.class;
    }

    public static class CustomDate extends Date {
        private final boolean createdWithLongConstructor;

        public CustomDate(long time) {
            super(time);
            createdWithLongConstructor = true;
        }
    }
}
