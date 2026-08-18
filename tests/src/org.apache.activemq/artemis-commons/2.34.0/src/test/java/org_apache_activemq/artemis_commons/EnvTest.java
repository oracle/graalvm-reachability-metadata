/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.artemis.utils.Env;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EnvTest {

    @Test
    @Order(1)
    public void reportsOsPageSizeFromRuntimeEnvironment() {
        int osPageSize = Env.osPageSize();

        assertThat(osPageSize).isPositive();
        assertThat(Integer.bitCount(osPageSize)).isEqualTo(1);
    }

    @Test
    @Order(3)
    public void togglesTestEnvironmentFlag() {
        boolean originalValue = Env.isTestEnv();
        try {
            Env.setTestEnv(true);
            assertThat(Env.isTestEnv()).isTrue();

            Env.setTestEnv(false);
            assertThat(Env.isTestEnv()).isFalse();
        } finally {
            Env.setTestEnv(originalValue);
        }
    }

    @Test
    @Order(4)
    public void identifiesCurrentOperatingSystemFamily() {
        boolean linux = Env.isLinuxOs();
        boolean mac = Env.isMacOs();
        boolean windows = Env.isWindowsOs();

        assertThat(linux || mac || windows).isEqualTo(isKnownOperatingSystem());
        assertThat((linux ? 1 : 0) + (mac ? 1 : 0) + (windows ? 1 : 0)).isLessThanOrEqualTo(1);
    }

    private static boolean isKnownOperatingSystem() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.startsWith("linux") || osName.startsWith("mac") || osName.startsWith("windows");
    }
}
