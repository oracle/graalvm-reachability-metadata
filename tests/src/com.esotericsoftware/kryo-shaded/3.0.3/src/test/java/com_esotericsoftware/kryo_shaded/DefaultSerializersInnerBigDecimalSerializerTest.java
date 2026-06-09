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
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

public class DefaultSerializersInnerBigDecimalSerializerTest {
    @Test
    void readsBigDecimalSubclassWithPublicBigIntegerAndScaleConstructor() {
        Kryo kryo = new Kryo();
        kryo.register(PreciseAmount.class, new DefaultSerializers.BigDecimalSerializer());

        PreciseAmount original = new PreciseAmount(new BigInteger("12345678901234567890"), 6);
        Output output = new Output(64, -1);
        kryo.writeObject(output, original);
        output.close();

        PreciseAmount restored = kryo.readObject(new Input(output.toBytes()), PreciseAmount.class);

        assertThat(restored).isInstanceOf(PreciseAmount.class);
        assertThat(restored.unscaledValue()).isEqualTo(original.unscaledValue());
        assertThat(restored.scale()).isEqualTo(original.scale());
        assertThat(restored).isEqualByComparingTo(original);
    }

    public static class PreciseAmount extends BigDecimal {
        public PreciseAmount(BigInteger unscaledValue, int scale) {
            super(unscaledValue, scale);
        }
    }
}
