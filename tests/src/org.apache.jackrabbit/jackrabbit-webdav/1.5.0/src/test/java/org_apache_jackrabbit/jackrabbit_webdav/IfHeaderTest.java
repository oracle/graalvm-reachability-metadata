/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.header.IfHeader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class IfHeaderTest {
    @Test
    void createsUntaggedHeaderFromLockTokensAndMatchesToken() {
        IfHeader header = new IfHeader(new String[] {"opaquelocktoken:alpha"});

        assertThat(header.getHeaderName()).isEqualTo(DavConstants.HEADER_IF);
        assertThat(header.getHeaderValue()).isEqualTo("(<opaquelocktoken:alpha>)");
        assertThat(header.hasValue()).isTrue();
        assertThat(header.matches(null, "opaquelocktoken:alpha", null)).isTrue();
        assertThat(header.matches(null, "opaquelocktoken:beta", null)).isFalse();
        assertThat(iteratorValues(header.getAllTokens())).contains("opaquelocktoken:alpha");
        assertThat(iteratorValues(header.getAllNotTokens())).isEmpty();
    }

    private static List iteratorValues(Iterator iterator) {
        List values = new ArrayList();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }
}
