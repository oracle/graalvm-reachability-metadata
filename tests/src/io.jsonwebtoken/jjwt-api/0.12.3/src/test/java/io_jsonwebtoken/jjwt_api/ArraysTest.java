/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_api;

import io.jsonwebtoken.lang.Arrays;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ArraysTest {

    @Test
    void copyReturnsNullForNullInput() {
        assertThat(Arrays.copy(null)).isNull();
    }

    @Test
    void lengthReturnsZeroForNullArrays() {
        assertThat(Arrays.length((String[]) null)).isZero();
        assertThat(Arrays.length((byte[]) null)).isZero();
    }

    @Test
    void asListReturnsEmptyListForNullAndEmptyArrays() {
        List<String> emptyValues = Arrays.asList(null);
        List<String> emptyArrayValues = Arrays.asList(new String[0]);

        assertThat(emptyValues).isEmpty();
        assertThat(emptyArrayValues).isEmpty();
    }

    @Test
    void asListExposesElementsForNonEmptyArrays() {
        List<String> values = Arrays.asList(new String[]{"header", "payload"});

        assertThat(values).containsExactly("header", "payload");
    }

    @Test
    void cleanReturnsNullForNullAndEmptyInputAndSameArrayForNonEmptyInput() {
        byte[] empty = new byte[0];
        byte[] value = new byte[]{1, 2, 3};

        assertThat(Arrays.clean(null)).isNull();
        assertThat(Arrays.clean(empty)).isNull();
        assertThat(Arrays.clean(value)).isSameAs(value);
    }

    @Test
    void copyClonesReferenceArrays() {
        String[] original = new String[]{"alg", "kid"};

        String[] copy = (String[]) Arrays.copy(original);
        copy[0] = "typ";

        assertThat(copy).containsExactly("typ", "kid");
        assertThat(original).containsExactly("alg", "kid");
    }

    @Test
    void copyPreservesRuntimeTypeForEmptyReferenceArrays() {
        HeaderValue[] original = new HeaderValue[0];

        HeaderValue[] copy = (HeaderValue[]) Arrays.copy(original);

        assertThat(copy).isEmpty();
        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getClass()).isEqualTo(original.getClass());
    }

    @Test
    void copyClonesPrimitiveArrays() {
        int[] original = new int[]{1, 2, 3};

        int[] copy = (int[]) Arrays.copy(original);
        copy[1] = 9;

        assertThat(copy).containsExactly(1, 9, 3);
        assertThat(original).containsExactly(1, 2, 3);
    }

    @Test
    void copyClonesRemainingPrimitiveArrayTypes() {
        boolean[] booleans = new boolean[]{true, false};
        byte[] bytes = new byte[]{1, 2};
        char[] chars = new char[]{'j', 'w'};
        double[] doubles = new double[]{1.5d, 2.5d};
        float[] floats = new float[]{1.0f, 2.0f};
        long[] longs = new long[]{3L, 4L};
        short[] shorts = new short[]{5, 6};

        assertThat((boolean[]) Arrays.copy(booleans)).containsExactly(true, false);
        assertThat((byte[]) Arrays.copy(bytes)).containsExactly((byte) 1, (byte) 2);
        assertThat((char[]) Arrays.copy(chars)).containsExactly('j', 'w');
        assertThat((double[]) Arrays.copy(doubles)).containsExactly(1.5d, 2.5d);
        assertThat((float[]) Arrays.copy(floats)).containsExactly(1.0f, 2.0f);
        assertThat((long[]) Arrays.copy(longs)).containsExactly(3L, 4L);
        assertThat((short[]) Arrays.copy(shorts)).containsExactly((short) 5, (short) 6);
    }

    @Test
    void copyClonesNestedPrimitiveArraysThroughObjectArrayPath() {
        int[][] original = new int[][]{{1, 2}, {3, 4}};

        int[][] copy = (int[][]) Arrays.copy(original);
        copy[0] = new int[]{9, 9};

        assertThat(copy[0]).containsExactly(9, 9);
        assertThat(copy[1]).containsExactly(3, 4);
        assertThat(original[0]).containsExactly(1, 2);
        assertThat(original[1]).containsExactly(3, 4);
    }

    @Test
    void copyRejectsNonArrayInput() {
        assertThatThrownBy(() -> Arrays.copy("not-an-array"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Argument must be an array.");
    }

    private static final class HeaderValue {
    }
}
