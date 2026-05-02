/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_platform.junit_platform_commons;

import java.awt.datatransfer.DataFlavor;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.Resource;
import org.junit.platform.commons.util.ModuleUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleUtilsInnerModuleReferenceResourceScannerTest {

    private static final String FLAVOR_MAP_RESOURCE = "sun/datatransfer/resources/flavormap.properties";

    @Test
    void scansNamedModuleAndLoadsMatchingResource() {
        String moduleName = DataFlavor.class.getModule().getName();

        List<Resource> resources = ModuleUtils.findAllResourcesInModule(moduleName,
                resource -> resource.getName().equals(FLAVOR_MAP_RESOURCE));

        assertThat(resources).singleElement().satisfies(resource -> {
            assertThat(resource.getName()).isEqualTo(FLAVOR_MAP_RESOURCE);
            assertThat(resource.getUri()).isNotNull();
        });
    }
}
