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
import com.esotericsoftware.kryo.serializers.DefaultSerializers.BigDecimalSerializer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultSerializersInnerBigDecimalSerializerTest {
    @Test
    void readsBigDecimalSubclassUsingPublicBigIntegerScaleConstructor() {
        Kryo kryo = new Kryo();
        BigDecimalSerializer serializer = new BigDecimalSerializer();
        ConstructorTrackingBigDecimal original = new ConstructorTrackingBigDecimal(
                new BigInteger("123456789012345678901234567890"),
                12);

        Output output = new Output(128, -1);
        kryo.writeObject(output, original, serializer);

        ConstructorTrackingBigDecimal restored = kryo.readObject(
                new Input(output.toBytes()),
                ConstructorTrackingBigDecimal.class,
                serializer);

        assertThat(restored).isInstanceOf(ConstructorTrackingBigDecimal.class);
        assertThat(restored).isEqualTo(original);
        assertThat(restored.scale()).isEqualTo(original.scale());
        assertThat(restored.wasCreatedByBigIntegerScaleConstructor()).isTrue();
    }

    public static final class ConstructorTrackingBigDecimal extends BigDecimal {
        private static final long serialVersionUID = 1L;

        private final boolean createdByBigIntegerScaleConstructor;

        public ConstructorTrackingBigDecimal(BigInteger unscaledValue, int scale) {
            super(unscaledValue, scale);
            this.createdByBigIntegerScaleConstructor = true;
        }

        boolean wasCreatedByBigIntegerScaleConstructor() {
            return createdByBigIntegerScaleConstructor;
        }
    }
}
