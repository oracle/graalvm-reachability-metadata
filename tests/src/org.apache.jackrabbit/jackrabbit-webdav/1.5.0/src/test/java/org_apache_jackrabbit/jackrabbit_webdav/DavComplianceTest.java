/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavCompliance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DavComplianceTest {
    @Test
    void concatenatesRequestedComplianceClasses() {
        String[] complianceClasses = {
                DavCompliance._1_,
                DavCompliance._2_,
                DavCompliance._3_,
                DavCompliance.VERSION_CONTROL,
                DavCompliance.ACCESS_CONTROL,
                DavCompliance.BIND
        };

        String headerValue = DavCompliance.concatComplianceClasses(complianceClasses);

        assertThat(headerValue).isEqualTo("1,2,3,version-control,access-control,bind");
    }
}
