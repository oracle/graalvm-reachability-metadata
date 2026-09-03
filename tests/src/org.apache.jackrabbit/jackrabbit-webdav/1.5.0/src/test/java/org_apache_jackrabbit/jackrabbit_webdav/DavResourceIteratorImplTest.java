/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DavResourceIteratorImplTest {
    @Test
    void iteratesOverInitialResourceListAndReportsFixedSize() {
        DavResourceIteratorImpl iterator = new DavResourceIteratorImpl(Collections.emptyList());

        assertThat(iterator.size()).isZero();
        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::remove)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Remove not allowed with DavResourceIteratorImpl");
    }
}
