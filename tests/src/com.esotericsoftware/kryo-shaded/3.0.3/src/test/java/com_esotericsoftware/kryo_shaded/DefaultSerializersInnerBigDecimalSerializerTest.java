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
import com.esotericsoftware.kryo.serializers.DefaultSerializers.BigDecimalSerializer;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

public class DefaultSerializersInnerBigDecimalSerializerTest {
    @Test
    void readsBigDecimalSubclassUsingPublicBigIntegerScaleConstructor() {
        Kryo kryo = new Kryo();
        BigDecimalSerializer serializer = new BigDecimalSerializer();
        CustomBigDecimal original = new CustomBigDecimal(new BigInteger("123456789012345678901234567890"), 6);

        Output output = new Output(128, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        BigDecimal read = serializer.read(kryo, input, customBigDecimalType());

        assertThat(read).isInstanceOf(CustomBigDecimal.class);
        assertThat(read.unscaledValue()).isEqualTo(original.unscaledValue());
        assertThat(read.scale()).isEqualTo(original.scale());
        assertThat(read).isEqualByComparingTo(original);
    }

    @SuppressWarnings("unchecked")
    private static Class<BigDecimal> customBigDecimalType() {
        return (Class<BigDecimal>) (Class<?>) CustomBigDecimal.class;
    }

    public static class CustomBigDecimal extends BigDecimal {
        public CustomBigDecimal(BigInteger unscaledValue, int scale) {
            super(unscaledValue, scale);
        }
    }
}
