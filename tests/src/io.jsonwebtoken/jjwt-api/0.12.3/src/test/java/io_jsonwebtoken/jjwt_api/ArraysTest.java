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
    void lengthReturnsZeroForNullArrays() {
        assertThat(Arrays.length((String[]) null)).isZero();
        assertThat(Arrays.length((byte[]) null)).isZero();
    }

    @Test
    void asListReturnsEmptyListForNullAndExposesElementsForNonEmptyArrays() {
        List<String> emptyValues = Arrays.asList(null);
        List<String> values = Arrays.asList(new String[]{"header", "payload"});

        assertThat(emptyValues).isEmpty();
        assertThat(values).containsExactly("header", "payload");
    }

    @Test
    void cleanReturnsNullForEmptyInputAndSameArrayForNonEmptyInput() {
        byte[] empty = new byte[0];
        byte[] value = new byte[]{1, 2, 3};

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
    void copyClonesPrimitiveArrays() {
        int[] original = new int[]{1, 2, 3};

        int[] copy = (int[]) Arrays.copy(original);
        copy[1] = 9;

        assertThat(copy).containsExactly(1, 9, 3);
        assertThat(original).containsExactly(1, 2, 3);
    }

    @Test
    void copyRejectsNonArrayInput() {
        assertThatThrownBy(() -> Arrays.copy("not-an-array"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Argument must be an array.");
    }
}
