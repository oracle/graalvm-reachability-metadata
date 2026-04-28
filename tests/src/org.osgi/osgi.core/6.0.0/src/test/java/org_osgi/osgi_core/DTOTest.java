/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.osgi.dto.DTO;

public class DTOTest extends DTO {
    public static final String IGNORED_STATIC_FIELD = "ignored";

    public long bundleId;
    public String symbolicName;
    public boolean active;

    @Test
    void toStringIncludesPublicInstanceFields() {
        bundleId = 42L;
        symbolicName = "example.bundle";
        active = true;

        String value = toString();

        assertThat(value)
                .startsWith("{")
                .endsWith("}")
                .contains("\"bundleId\":42")
                .contains("\"symbolicName\":\"example.bundle\"")
                .contains("\"active\":true")
                .doesNotContain("IGNORED_STATIC_FIELD");
    }
}
