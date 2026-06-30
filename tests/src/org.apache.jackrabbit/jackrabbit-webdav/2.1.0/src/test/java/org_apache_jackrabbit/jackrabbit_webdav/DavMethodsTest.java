/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavMethods;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DavMethodsTest {
    @Test
    void resolvesRegisteredMethodCodes() {
        assertThat(DavMethods.getMethodCode("OPTIONS")).isEqualTo(DavMethods.DAV_OPTIONS);
        assertThat(DavMethods.getMethodCode("PROPFIND")).isEqualTo(DavMethods.DAV_PROPFIND);
        assertThat(DavMethods.getMethodCode("VERSION-CONTROL")).isEqualTo(DavMethods.DAV_VERSION_CONTROL);
        assertThat(DavMethods.getMethodCode("ACL")).isEqualTo(DavMethods.DAV_ACL);
        assertThat(DavMethods.getMethodCode("BIND")).isEqualTo(DavMethods.DAV_BIND);
    }

    @Test
    void resolvesMethodNamesCaseInsensitively() {
        assertThat(DavMethods.getMethodCode("mkcol")).isEqualTo(DavMethods.DAV_MKCOL);
        assertThat(DavMethods.getMethodCode("baseline-control")).isEqualTo(DavMethods.DAV_BASELINE_CONTROL);
    }

    @Test
    void returnsZeroForUnknownMethodName() {
        assertThat(DavMethods.getMethodCode("PATCH")).isZero();
    }
}
