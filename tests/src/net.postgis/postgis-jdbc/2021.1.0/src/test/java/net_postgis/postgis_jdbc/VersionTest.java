/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_postgis.postgis_jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.postgis.Version;

public class VersionTest {

    @Test
    void exposesVersionLoadedFromBundledPropertiesResource() {
        assertThat(Version.VERSION).isNotBlank();
        assertThat(Version.MAJOR).isGreaterThanOrEqualTo(0);
        assertThat(Version.MINOR).isGreaterThanOrEqualTo(0);
        assertThat(Version.MICRO).isNotBlank();

        String expectedFullVersion = "PostGIS JDBC V" + Version.MAJOR + "." + Version.MINOR + "."
                + Version.MICRO;
        assertThat(Version.getFullVersion()).isEqualTo(expectedFullVersion);
        assertThat(Version.VERSION)
                .startsWith(Version.MAJOR + "." + Version.MINOR + ".");
    }
}
