/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.converters.ByteConverter;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteConverterTest {
    @Test
    public void encodesWrapperByteArraysAsPrimitiveByteArrays() {
        final ByteConverter converter = new ByteConverter();

        final Object encoded = converter.encode(new Byte[] {1, 2, 3});

        assertThat(encoded).isInstanceOf(byte[].class);
        assertThat((byte[]) encoded).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    public void decodesPrimitiveByteArraysAsWrapperByteArrays() {
        final ByteConverter converter = new ByteConverter();

        final Object decoded = converter.decode(Byte[].class, new byte[] {4, 5, 6});

        assertThat(decoded).isInstanceOf(Byte[].class);
        assertThat((Byte[]) decoded).containsExactly((byte) 4, (byte) 5, (byte) 6);
    }
}
