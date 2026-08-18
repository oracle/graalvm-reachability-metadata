/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.CheckinMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CheckinMethodTest {
    @Test
    void createsCheckinRequestMethod() {
        CheckinMethod method = new CheckinMethod("/repository/default/versionable-node");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_CHECKIN);
    }
}
