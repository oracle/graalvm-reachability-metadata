/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.osgi.framework.dto.BundleDTO;
import org.osgi.framework.dto.FrameworkDTO;

public class DTOTest {
    @Test
    void toStringReadsPublicFieldsFromFrameworkDto() {
        BundleDTO bundle = new BundleDTO();
        bundle.id = 7L;
        bundle.lastModified = 1234L;
        bundle.state = 32;
        bundle.symbolicName = "example.bundle";
        bundle.version = "1.2.3";

        String rendered = bundle.toString();

        assertThat(rendered)
                .startsWith("{")
                .endsWith("}")
                .contains("\"id\":7")
                .contains("\"lastModified\":1234")
                .contains("\"state\":32")
                .contains("\"symbolicName\":\"example.bundle\"")
                .contains("\"version\":\"1.2.3\"");
    }

    @Test
    void toStringRendersNestedDtosAndAggregates() {
        BundleDTO bundle = new BundleDTO();
        bundle.id = 42L;
        bundle.symbolicName = "nested.bundle";

        FrameworkDTO framework = new FrameworkDTO();
        framework.bundles = List.of(bundle);
        framework.properties = Map.of("framework.name", "OSGi", "active", Boolean.TRUE);
        framework.services = List.of();

        String rendered = framework.toString();

        assertThat(rendered)
                .contains("\"bundles\":[{")
                .contains("\"id\":42")
                .contains("\"symbolicName\":\"nested.bundle\"")
                .contains("\"properties\":{")
                .contains("\"framework.name\":\"OSGi\"")
                .contains("\"active\":true")
                .contains("\"services\":[]");
    }
}
