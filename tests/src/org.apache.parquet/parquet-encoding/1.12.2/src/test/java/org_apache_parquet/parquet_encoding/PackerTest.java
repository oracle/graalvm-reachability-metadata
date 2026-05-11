/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_encoding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;

import org.apache.parquet.column.values.bitpacking.BytePacker;
import org.apache.parquet.column.values.bitpacking.BytePackerForLong;
import org.apache.parquet.column.values.bitpacking.IntPacker;
import org.apache.parquet.column.values.bitpacking.Packer;
import org.junit.jupiter.api.Test;

public class PackerTest {
    @Test
    void createsGeneratedPackersAndRoundTripsValues() {
        for (Packer packerFactory : Packer.values()) {
            assertIntPackerRoundTrip(packerFactory);
            assertBytePackerRoundTrip(packerFactory);
            assertLongBytePackerRoundTrip(packerFactory);
        }
    }

    private static void assertIntPackerRoundTrip(Packer packerFactory) {
        int bitWidth = 5;
        IntPacker packer = packerFactory.newIntPacker(bitWidth);
        int[] values = new int[32];
        for (int i = 0; i < values.length; i++) {
            values[i] = i % (1 << bitWidth);
        }

        int[] packed = new int[bitWidth];
        int[] unpacked = new int[values.length];
        packer.pack32Values(values, 0, packed, 0);
        packer.unpack32Values(packed, 0, unpacked, 0);

        assertEquals(bitWidth, packer.getBitWidth());
        assertArrayEquals(values, unpacked);
    }

    private static void assertBytePackerRoundTrip(Packer packerFactory) {
        int bitWidth = 6;
        BytePacker packer = packerFactory.newBytePacker(bitWidth);
        int[] values = new int[32];
        for (int i = 0; i < values.length; i++) {
            values[i] = (i * 3) % (1 << bitWidth);
        }

        byte[] packed = new byte[bitWidth * 4];
        int[] unpacked = new int[values.length];
        packer.pack32Values(values, 0, packed, 0);
        packer.unpack32Values(ByteBuffer.wrap(packed), 0, unpacked, 0);

        assertEquals(bitWidth, packer.getBitWidth());
        assertArrayEquals(values, unpacked);
    }

    private static void assertLongBytePackerRoundTrip(Packer packerFactory) {
        int bitWidth = 7;
        BytePackerForLong packer = packerFactory.newBytePackerForLong(bitWidth);
        long[] values = new long[32];
        for (int i = 0; i < values.length; i++) {
            values[i] = (long) (i * 5) % (1L << bitWidth);
        }

        byte[] packed = new byte[bitWidth * 4];
        long[] unpacked = new long[values.length];
        packer.pack32Values(values, 0, packed, 0);
        packer.unpack32Values(ByteBuffer.wrap(packed), 0, unpacked, 0);

        assertEquals(bitWidth, packer.getBitWidth());
        assertArrayEquals(values, unpacked);
    }
}
