/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_plexus.plexus_sec_dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sonatype.plexus.components.sec.dispatcher.model.Config;
import org.sonatype.plexus.components.sec.dispatcher.model.SettingsSecurity;

public class SettingsSecurityTest {
    @Test
    void managesConfigurationsAndRejectsNullConfiguration() {
        SettingsSecurity settingsSecurity = new SettingsSecurity();
        Config configuration = new Config();

        settingsSecurity.addConfiguration(configuration);

        assertThat(settingsSecurity.getConfigurations()).containsExactly(configuration);
        assertThatThrownBy(() -> settingsSecurity.addConfiguration(null))
                .isInstanceOf(ClassCastException.class);
    }
}
