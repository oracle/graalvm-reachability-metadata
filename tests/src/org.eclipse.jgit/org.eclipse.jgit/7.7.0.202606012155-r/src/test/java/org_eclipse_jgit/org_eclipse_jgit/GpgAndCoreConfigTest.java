/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jgit.org_eclipse_jgit;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.CoreConfig.TrustStat;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgConfig.GpgFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GpgAndCoreConfigTest {

    @Test
    void readsTypedGpgAndCoreConfiguration() {
        Config config = new Config();
        config.setString(ConfigConstants.CONFIG_GPG_SECTION, null,
                ConfigConstants.CONFIG_KEY_FORMAT, "ssh");
        config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
                ConfigConstants.CONFIG_KEY_TRUST_STAT, "never");

        GpgConfig gpgConfig = new GpgConfig(config);
        CoreConfig coreConfig = config.get(CoreConfig.KEY);

        assertThat(gpgConfig.getKeyFormat()).isEqualTo(GpgFormat.SSH);
        assertThat(coreConfig.getTrustPackedRefsStat()).isEqualTo(TrustStat.NEVER);
        assertThat(coreConfig.getTrustLooseRefStat()).isEqualTo(TrustStat.NEVER);
    }
}
