/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander.defaultprovider;

import com.beust.jcommander.defaultprovider.PropertyFileDefaultProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyFileDefaultProviderTest {
    @Test
    void loadsDefaultValuesFromClasspathPropertyFile() {
        PropertyFileDefaultProvider provider = new PropertyFileDefaultProvider();

        assertThat(provider.getDefaultValueFor("--host")).isEqualTo("localhost");
        assertThat(provider.getDefaultValueFor("-port")).isEqualTo("8080");
    }
}
