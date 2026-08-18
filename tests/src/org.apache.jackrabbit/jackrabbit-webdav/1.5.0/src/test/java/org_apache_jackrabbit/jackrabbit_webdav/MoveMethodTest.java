/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.MoveMethod;
import org.apache.jackrabbit.webdav.header.OverwriteHeader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MoveMethodTest {
    @Test
    void createsMoveRequestWithDestinationAndOverwriteHeaders() {
        MoveMethod method = new MoveMethod(
                "/repository/source.txt",
                "http://localhost/repository/destination.txt",
                false);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_MOVE);
        assertThat(method.getRequestHeader(DavConstants.HEADER_DESTINATION).getValue())
                .isEqualTo("http://localhost/repository/destination.txt");
        assertThat(method.getRequestHeader(DavConstants.HEADER_OVERWRITE).getValue())
                .isEqualTo(OverwriteHeader.OVERWRITE_FALSE);
    }
}
