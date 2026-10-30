/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_core;

import org.apache.calcite.avatica.DriverVersion;
import org.apache.calcite.avatica.remote.Driver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DriverVersionTest {
    @Test
    void loadsDriverIdentityAndNumericComponentsFromClasspathProperties() {
        DriverVersion version = DriverVersion.load(Driver.class, "avatica-test-driver.properties",
                "fallback driver", "unknown", "fallback product", "unknown");
        assertEquals("Warehouse JDBC", version.name);
        assertEquals("Warehouse", version.productName);
        assertEquals("7.3-fixture", version.versionString);
        assertEquals("11.2-fixture", version.productVersion);
        assertEquals(7, version.majorVersion);
        assertEquals(3, version.minorVersion);
        assertEquals(11, version.databaseMajorVersion);
        assertEquals(2, version.databaseMinorVersion);
        assertFalse(version.jdbcCompliant);
    }
}
