/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.util.LangUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LangUtilTest {
    @Test
    void safeCopyCreatesTypedArrayWhenSinkHasWrongSize() {
        Object[] source = {"alpha", new StringBuilder("ignored"), "beta", null};
        String[] sink = new String[0];

        Object[] copy = LangUtil.safeCopy(source, sink);

        assertThat(copy).isInstanceOf(String[].class);
        assertThat(copy).isNotSameAs(sink);
        assertThat(copy).containsExactly("alpha", "beta");
    }
}
