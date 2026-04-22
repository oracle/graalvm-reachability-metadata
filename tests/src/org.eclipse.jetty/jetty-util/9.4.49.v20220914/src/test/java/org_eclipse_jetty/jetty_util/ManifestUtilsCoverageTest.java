/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.util.Optional;

import org.eclipse.jetty.util.ManifestUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ManifestUtilsCoverageTest {
    @Test
    void manifestUtilsQueriesModuleVersionWhenNoManifestIsAvailable() {
        Optional<String> version = ManifestUtils.getVersion(String.class);

        assertThat(version).isNotNull();
    }
}
