/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_R4_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Hashtable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;

public class FrameworkUtilAnonymous1Test {
    private static final String IMPLEMENTATION_PROPERTY = "org.osgi.vendor.framework";
    private static final String IMPLEMENTATION_PACKAGE =
            "org_osgi.osgi_R4_core.vendor.framework";

    private static String previousImplementationPackage;

    @BeforeAll
    static void setUpVendorImplementation() {
        previousImplementationPackage = System.getProperty(IMPLEMENTATION_PROPERTY);
        System.setProperty(IMPLEMENTATION_PROPERTY, IMPLEMENTATION_PACKAGE);
    }

    @AfterAll
    static void restoreVendorImplementation() {
        if (previousImplementationPackage == null) {
            System.clearProperty(IMPLEMENTATION_PROPERTY);
        } else {
            System.setProperty(IMPLEMENTATION_PROPERTY, previousImplementationPackage);
        }
    }

    @BeforeEach
    void resetVendorState() {
        org_osgi.osgi_R4_core.vendor.framework.FrameworkUtil.reset();
    }

    @Test
    void createFilterLoadsConfiguredVendorImplementation() throws InvalidSyntaxException {
        Filter filter = FrameworkUtil.createFilter("(name=osgi)");

        Hashtable<String, String> matchingProperties = new Hashtable<String, String>();
        matchingProperties.put("name", "osgi");
        Hashtable<String, String> nonMatchingProperties = new Hashtable<String, String>();
        nonMatchingProperties.put("name", "other");

        assertThat(filter.match(matchingProperties)).isTrue();
        assertThat(filter.match(nonMatchingProperties)).isFalse();
        assertThat(filter.toString()).isEqualTo("(name=osgi)");
        assertThat(org_osgi.osgi_R4_core.vendor.framework.FrameworkUtil.createFilterCalls)
                .isEqualTo(1);
        assertThat(org_osgi.osgi_R4_core.vendor.framework.FrameworkUtil.lastFilter)
                .isEqualTo("(name=osgi)");
    }
}
