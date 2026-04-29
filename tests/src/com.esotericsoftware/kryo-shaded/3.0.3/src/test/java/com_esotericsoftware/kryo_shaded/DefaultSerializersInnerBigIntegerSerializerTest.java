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
import com.esotericsoftware.kryo.serializers.DefaultSerializers.BigIntegerSerializer;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultSerializersInnerBigIntegerSerializerTest {
    @Test
    void readsBigIntegerSubclassUsingPublicByteArrayConstructor() {
        Kryo kryo = new Kryo();
        BigIntegerSerializer serializer = new BigIntegerSerializer();
        ConstructorTrackingBigInteger original = new ConstructorTrackingBigInteger(
                new byte[] {1, 35, 69, 103, -119, -85, -51, -17});

        Output output = new Output(128, -1);
        kryo.writeObject(output, original, serializer);

        ConstructorTrackingBigInteger restored = kryo.readObject(
                new Input(output.toBytes()),
                ConstructorTrackingBigInteger.class,
                serializer);

        assertThat(restored).isInstanceOf(ConstructorTrackingBigInteger.class);
        assertThat(restored).isEqualTo(original);
        assertThat(restored.wasCreatedByByteArrayConstructor()).isTrue();
    }

    public static final class ConstructorTrackingBigInteger extends BigInteger {
        private static final long serialVersionUID = 1L;

        private final boolean createdByByteArrayConstructor;

        public ConstructorTrackingBigInteger(byte[] value) {
            super(value);
            this.createdByByteArrayConstructor = true;
        }

        boolean wasCreatedByByteArrayConstructor() {
            return createdByByteArrayConstructor;
        }
    }
}
