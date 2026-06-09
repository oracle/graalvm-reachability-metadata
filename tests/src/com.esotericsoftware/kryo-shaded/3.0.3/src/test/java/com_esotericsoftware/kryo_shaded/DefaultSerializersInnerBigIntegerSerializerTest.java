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
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

public class DefaultSerializersInnerBigIntegerSerializerTest {
    @Test
    void readsBigIntegerSubclassWithPublicByteArrayConstructor() {
        Kryo kryo = new Kryo();
        kryo.register(SignedIdentifier.class, new DefaultSerializers.BigIntegerSerializer());

        SignedIdentifier original = new SignedIdentifier(new BigInteger("987654321098765432109876543210").toByteArray());
        Output output = new Output(64, -1);
        kryo.writeObject(output, original);
        output.close();

        SignedIdentifier restored = kryo.readObject(new Input(output.toBytes()), SignedIdentifier.class);

        assertThat(restored).isInstanceOf(SignedIdentifier.class);
        assertThat(restored).isEqualTo(original);
        assertThat(restored.toByteArray()).isEqualTo(original.toByteArray());
    }

    public static class SignedIdentifier extends BigInteger {
        public SignedIdentifier(byte[] value) {
            super(value);
        }
    }
}
