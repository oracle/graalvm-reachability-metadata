/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavCompliance;
import org.junit.jupiter.api.Test;

public class DavComplianceTest {
    @Test
    public void concatComplianceClassesInitializesComplianceClass() {
        String complianceHeader = DavCompliance.concatComplianceClasses(new String[] {
                DavCompliance._1_,
                DavCompliance._2_,
                DavCompliance.ACCESS_CONTROL,
                DavCompliance.ORDERED_COLLECTIONS
        });

        assertThat(complianceHeader).isEqualTo("1,2,access-control,ordered-collections");
    }
}
