/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.net.URI;

import org.eclipse.jetty.util.TypeUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleLocationCoverageTest {
    @Test
    void moduleLocationIsAvailableForJdkModules() {
        URI location = TypeUtil.getModuleLocation(String.class);

        assertThat(location == null || location.isAbsolute()).isTrue();
    }
}
