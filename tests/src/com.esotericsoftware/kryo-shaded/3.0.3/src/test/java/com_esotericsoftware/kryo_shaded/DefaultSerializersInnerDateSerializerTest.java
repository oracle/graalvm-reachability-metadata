/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.DefaultSerializers.DateSerializer;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultSerializersInnerDateSerializerTest {
    @Test
    void readsDateSubclassUsingPublicLongConstructor() {
        Kryo kryo = new Kryo();
        DateSerializer serializer = new DateSerializer();
        ConstructorTrackingDate original = new ConstructorTrackingDate(1_714_352_096_123L);

        Output output = new Output(64, -1);
        kryo.writeObject(output, original, serializer);

        ConstructorTrackingDate restored = kryo.readObject(
                new Input(output.toBytes()),
                ConstructorTrackingDate.class,
                serializer);

        assertThat(restored).isInstanceOf(ConstructorTrackingDate.class);
        assertThat(restored.getTime()).isEqualTo(original.getTime());
        assertThat(restored.wasCreatedByLongConstructor()).isTrue();
    }

    public static final class ConstructorTrackingDate extends Date {
        private static final long serialVersionUID = 1L;

        private final boolean createdByLongConstructor;

        public ConstructorTrackingDate(long time) {
            super(time);
            this.createdByLongConstructor = true;
        }

        boolean wasCreatedByLongConstructor() {
            return createdByLongConstructor;
        }
    }
}
