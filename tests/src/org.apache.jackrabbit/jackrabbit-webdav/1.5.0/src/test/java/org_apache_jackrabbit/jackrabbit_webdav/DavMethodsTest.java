/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavMethods;
import org.junit.jupiter.api.Test;

public class DavMethodsTest {
    @Test
    public void getMethodCodeResolvesRegisteredMethodsCaseInsensitively() {
        assertThat(DavMethods.getMethodCode("OPTIONS")).isEqualTo(DavMethods.DAV_OPTIONS);
        assertThat(DavMethods.getMethodCode("get")).isEqualTo(DavMethods.DAV_GET);
        assertThat(DavMethods.getMethodCode("propfind")).isEqualTo(DavMethods.DAV_PROPFIND);
        assertThat(DavMethods.getMethodCode("VERSION-CONTROL")).isEqualTo(DavMethods.DAV_VERSION_CONTROL);
        assertThat(DavMethods.getMethodCode("bind")).isEqualTo(DavMethods.DAV_BIND);
    }

    @Test
    public void getMethodCodeReturnsZeroForUnregisteredMethod() {
        assertThat(DavMethods.getMethodCode("UNKNOWN-WEBDAV-METHOD")).isZero();
    }
}
