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
    void safeCopyCreatesTypedArrayWhenFilteredResultDoesNotFitSink() {
        Object[] source = {"first", Integer.valueOf(7), "second"};
        String[] sink = new String[3];

        Object[] copied = LangUtil.safeCopy(source, sink);

        assertThat(copied).isInstanceOf(String[].class);
        assertThat((String[]) copied).containsExactly("first", "second");
    }
}
