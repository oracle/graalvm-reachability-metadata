/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_onnxruntime.onnxruntime;

import static org.assertj.core.api.Assertions.assertThat;

import ai.onnxruntime.OrtUtil;
import org.junit.jupiter.api.Test;

public class OrtUtilTest {
    private static final long[] MATRIX_SHAPE = {2, 2};

    @Test
    void createsTypedMultidimensionalArrays() {
        boolean[][] booleans = (boolean[][]) OrtUtil.newBooleanArray(MATRIX_SHAPE);
        byte[][] bytes = (byte[][]) OrtUtil.newByteArray(MATRIX_SHAPE);
        short[][] shorts = (short[][]) OrtUtil.newShortArray(MATRIX_SHAPE);
        int[][] integers = (int[][]) OrtUtil.newIntArray(MATRIX_SHAPE);
        long[][] longs = (long[][]) OrtUtil.newLongArray(MATRIX_SHAPE);
        float[][] floats = (float[][]) OrtUtil.newFloatArray(MATRIX_SHAPE);
        double[][] doubles = (double[][]) OrtUtil.newDoubleArray(MATRIX_SHAPE);
        String[][] strings = (String[][]) OrtUtil.newStringArray(MATRIX_SHAPE);

        assertThat(booleans.length).isEqualTo(2);
        assertThat(booleans[0]).containsExactly(false, false);
        assertThat(booleans[1]).containsExactly(false, false);
        assertThat(bytes.length).isEqualTo(2);
        assertThat(bytes[0]).containsExactly((byte) 0, (byte) 0);
        assertThat(bytes[1]).containsExactly((byte) 0, (byte) 0);
        assertThat(shorts.length).isEqualTo(2);
        assertThat(shorts[0]).containsExactly((short) 0, (short) 0);
        assertThat(shorts[1]).containsExactly((short) 0, (short) 0);
        assertThat(integers.length).isEqualTo(2);
        assertThat(integers[0]).containsExactly(0, 0);
        assertThat(integers[1]).containsExactly(0, 0);
        assertThat(longs.length).isEqualTo(2);
        assertThat(longs[0]).containsExactly(0L, 0L);
        assertThat(longs[1]).containsExactly(0L, 0L);
        assertThat(floats.length).isEqualTo(2);
        assertThat(floats[0]).containsExactly(0.0f, 0.0f);
        assertThat(floats[1]).containsExactly(0.0f, 0.0f);
        assertThat(doubles.length).isEqualTo(2);
        assertThat(doubles[0]).containsExactly(0.0d, 0.0d);
        assertThat(doubles[1]).containsExactly(0.0d, 0.0d);
        assertThat(strings.length).isEqualTo(2);
        assertThat(strings[0][0]).isNull();
        assertThat(strings[0][1]).isNull();
        assertThat(strings[1][0]).isNull();
        assertThat(strings[1][1]).isNull();
    }
}
