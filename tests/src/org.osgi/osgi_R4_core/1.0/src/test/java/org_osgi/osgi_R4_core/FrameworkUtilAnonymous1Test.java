/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Hashtable;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;

public class FrameworkUtilAnonymous1Test {
    static {
        System.setProperty("org.osgi.vendor.framework", "org_osgi.osgi_R4_core.vendor");
    }

    @Test
    void createFilterLoadsVendorFrameworkUtilAndFindsCreateFilterMethod() throws Exception {
        Filter filter = FrameworkUtil.createFilter("(service=test)");
        Hashtable<String, String> properties = new Hashtable<String, String>();
        properties.put("service", "test");

        assertThat(filter.toString()).isEqualTo("(service=test)");
        assertThat(filter.match(properties)).isTrue();
    }
}
