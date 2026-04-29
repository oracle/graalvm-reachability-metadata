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
import com.esotericsoftware.kryo.serializers.DefaultSerializers.BigIntegerSerializer;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

public class DefaultSerializersInnerBigIntegerSerializerTest {
    @Test
    void readsBigIntegerSubclassUsingPublicByteArrayConstructor() {
        Kryo kryo = new Kryo();
        BigIntegerSerializer serializer = new BigIntegerSerializer();
        CustomBigInteger original = new CustomBigInteger(new byte[] {1, 35, 69, 103, -119, -85, -51, -17});

        Output output = new Output(128, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        BigInteger read = serializer.read(kryo, input, customBigIntegerType());

        assertThat(read).isInstanceOf(CustomBigInteger.class);
        assertThat(read).isEqualTo(original);
        assertThat(read.toByteArray()).containsExactly(original.toByteArray());
    }

    @SuppressWarnings("unchecked")
    private static Class<BigInteger> customBigIntegerType() {
        return (Class<BigInteger>) (Class<?>) CustomBigInteger.class;
    }

    public static class CustomBigInteger extends BigInteger {
        public CustomBigInteger(byte[] value) {
            super(value);
        }
    }
}
