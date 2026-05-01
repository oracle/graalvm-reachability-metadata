/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import net.sourceforge.htmlunit.corejs.javascript.NativeArray;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeArrayTest {
    @Test
    void createsTypedArrayWhenSuppliedArrayIsTooSmall() {
        NativeArray array = new NativeArray(new Object[] {"alpha", "bravo", "charlie"});
        String[] target = new String[0];

        String[] values = (String[]) array.toArray(target);

        assertThat(values).containsExactly("alpha", "bravo", "charlie");
        assertThat(values).isNotSameAs(target);
        assertThat(values.getClass()).isEqualTo(String[].class);
    }
}
