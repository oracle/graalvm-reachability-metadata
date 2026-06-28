/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.CopyMethod;
import org.apache.jackrabbit.webdav.header.OverwriteHeader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CopyMethodTest {
    @Test
    void createsShallowCopyRequestWithDestinationAndOverwriteHeaders() {
        CopyMethod method = new CopyMethod(
                "/repository/source.txt",
                "http://localhost/repository/destination.txt",
                false,
                true);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_COPY);
        assertThat(method.getRequestHeader(DavConstants.HEADER_DESTINATION).getValue())
                .isEqualTo("http://localhost/repository/destination.txt");
        assertThat(method.getRequestHeader(DavConstants.HEADER_OVERWRITE).getValue())
                .isEqualTo(OverwriteHeader.OVERWRITE_FALSE);
        assertThat(method.getRequestHeader(DavConstants.HEADER_DEPTH).getValue()).isEqualTo("0");
    }
}
