/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.converters.ByteConverter;

public class ByteConverterTest {
    @Test
    void encodesWrapperArrayAsPrimitiveArray() {
        ByteConverter converter = new ByteConverter();
        Byte[] values = new Byte[] {Byte.valueOf((byte) 1), Byte.valueOf((byte) 2), Byte.valueOf((byte) -3)};

        Object encoded = converter.encode(values);

        assertThat(encoded).isInstanceOf(byte[].class);
        assertThat((byte[]) encoded).containsExactly((byte) 1, (byte) 2, (byte) -3);
    }

    @Test
    void decodesPrimitiveArrayAsWrapperArray() {
        ByteConverter converter = new ByteConverter();
        byte[] values = new byte[] {(byte) 4, (byte) 5, (byte) -6};

        Object decoded = converter.decode(Byte[].class, values);

        assertThat(decoded).isInstanceOf(Byte[].class);
        assertThat((Byte[]) decoded).containsExactly(
                Byte.valueOf((byte) 4), Byte.valueOf((byte) 5), Byte.valueOf((byte) -6));
    }
}
