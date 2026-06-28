/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl.ognl;

import ognl.OgnlOps;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class OgnlOpsTest {
    @Test
    void convertsCollectionToTypedArray() {
        final Object result = OgnlOps.toArray(Arrays.asList("alpha", "bravo"), String.class);

        assertThat(result).isInstanceOf(String[].class);
        assertThat((String[]) result).containsExactly("alpha", "bravo");
    }

    @Test
    void convertsScalarToSingleElementTypedArray() {
        final Object result = OgnlOps.toArray("42", Integer.class);

        assertThat(result).isInstanceOf(Integer[].class);
        assertThat((Integer[]) result).containsExactly(42);
    }

    @Test
    void convertsArrayElementsToRequestedComponentType() {
        final Object result = OgnlOps.toArray(new String[] {"7", "8"}, Integer.class);

        assertThat(result).isInstanceOf(Integer[].class);
        assertThat((Integer[]) result).containsExactly(7, 8);
    }

    @Test
    void convertsArrayToRequestedArrayType() {
        final Object result = OgnlOps.convertValue(new String[] {"3", "5"}, Long[].class);

        assertThat(result).isInstanceOf(Long[].class);
        assertThat((Long[]) result).containsExactly(3L, 5L);
    }
}
