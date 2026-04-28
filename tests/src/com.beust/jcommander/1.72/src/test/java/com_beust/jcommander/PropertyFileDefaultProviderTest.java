/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.defaultprovider.PropertyFileDefaultProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyFileDefaultProviderTest {
    @Test
    void loadsDefaultValuesFromClasspathPropertiesFile() {
        PropertyFileDefaultProvider provider = new PropertyFileDefaultProvider(
                "com_beust/jcommander/property-file-default-provider.properties");

        assertThat(provider.getDefaultValueFor("--host")).isEqualTo("example.org");
        assertThat(provider.getDefaultValueFor("-port")).isEqualTo("443");
        assertThat(provider.getDefaultValueFor("timeout")).isEqualTo("30");
    }
}
