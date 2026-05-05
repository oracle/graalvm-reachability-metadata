/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core;

import java.util.Dictionary;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class FrameworkUtilAnonymous1Test {
    @Test
    void createFilterLoadsConfiguredVendorDelegate() throws Exception {
        System.setProperty("org.osgi.vendor.framework", "org_osgi.osgi_R4_core.vendor");

        Filter filter = FrameworkUtil.createFilter("(service.name=example)");
        Dictionary<Object, Object> matchingProperties = new Properties();
        matchingProperties.put("service.name", "example");
        Dictionary<Object, Object> nonMatchingProperties = new Properties();
        nonMatchingProperties.put("service.name", "other");

        assertThat(filter.toString()).isEqualTo("(service.name=example)");
        assertThat(filter.matchCase(matchingProperties)).isTrue();
        assertThat(filter.matchCase(nonMatchingProperties)).isFalse();
    }
}
