/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import org.jets3t.service.Jets3tProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jets3tPropertiesTest {
    private static final String PROPERTIES_RESOURCE = "jets3tproperties-coverage.properties";

    @Test
    public void loadsPropertiesFromClasspathResource() {
        Jets3tProperties properties = Jets3tProperties.getInstance(PROPERTIES_RESOURCE);

        assertThat(properties.isLoaded()).isTrue();
        assertThat(properties.containsKey("coverage.string")).isTrue();
        assertThat(properties.getStringProperty("coverage.string", "fallback")).isEqualTo("trimmed value");
        assertThat(properties.getLongProperty("coverage.long", -1L)).isEqualTo(42L);
        assertThat(properties.getIntProperty("coverage.int", -1)).isEqualTo(7);
        assertThat(properties.getBoolProperty("coverage.bool", false)).isTrue();
        assertThat(properties.getBoolProperty("coverage.false", true)).isFalse();
    }
}
