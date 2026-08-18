/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.commons.httpclient.Header;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.UnLockMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnLockMethodTest {
    @Test
    void createsUnlockRequestWithLockTokenHeader() {
        String lockToken = "opaquelocktoken:12345678-1234-1234-1234-123456789abc";
        UnLockMethod method = new UnLockMethod("/repository/default/document.txt", lockToken);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_UNLOCK);
        assertThat(method.getPath()).isEqualTo("/repository/default/document.txt");

        Header lockTokenHeader = method.getRequestHeader(DavConstants.HEADER_LOCK_TOKEN);
        assertThat(lockTokenHeader).isNotNull();
        assertThat(lockTokenHeader.getValue()).isEqualTo("<" + lockToken + ">");
    }
}
