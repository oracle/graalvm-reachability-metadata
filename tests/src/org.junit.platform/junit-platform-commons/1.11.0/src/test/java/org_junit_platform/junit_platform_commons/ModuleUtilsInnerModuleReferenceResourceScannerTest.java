/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_platform.junit_platform_commons;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.Resource;
import org.junit.platform.commons.util.ModuleUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleUtilsInnerModuleReferenceResourceScannerTest {

    @Test
    void loadsResourcesDiscoveredInNamedModule() {
        List<Resource> resources = ModuleUtils.findAllResourcesInModule("java.base",
                resource -> resource.getName().equals("java/lang/uniName.dat"));

        assertThat(resources).extracting(Resource::getName).contains("java/lang/uniName.dat");
    }
}
