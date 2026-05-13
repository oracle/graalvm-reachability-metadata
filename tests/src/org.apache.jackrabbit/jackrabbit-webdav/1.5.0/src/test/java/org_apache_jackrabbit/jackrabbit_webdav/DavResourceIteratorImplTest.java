/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;

import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.junit.jupiter.api.Test;

public class DavResourceIteratorImplTest {
    @Test
    public void constructorInitializesLoggerAndIteratesInitialList() {
        DavResourceIteratorImpl iterator = new DavResourceIteratorImpl(Collections.<DavResource>singletonList(null));

        assertThat(iterator.size()).isEqualTo(1);
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.nextResource()).isNull();
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    public void removeIsUnsupported() {
        DavResourceIteratorImpl iterator = new DavResourceIteratorImpl(Collections.emptyList());

        assertThatThrownBy(iterator::remove)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Remove not allowed with DavResourceIteratorImpl");
    }
}
