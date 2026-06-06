/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl.ognl;

import ognl.ArrayPropertyAccessor;
import ognl.DynamicSubscript;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayPropertyAccessorTest {
    @Test
    void returnsCopiedArrayForAllDynamicSubscript() throws Exception {
        final Map<?, ?> context = Collections.emptyMap();
        final ArrayPropertyAccessor accessor = new ArrayPropertyAccessor();
        final String[] source = {"alpha", "beta", "gamma"};

        final Object result = accessor.getProperty(context, source, DynamicSubscript.all);

        assertThat(result).isInstanceOf(String[].class);
        assertThat(result).isNotSameAs(source);
        assertThat((String[]) result).containsExactly(source);
    }
}
