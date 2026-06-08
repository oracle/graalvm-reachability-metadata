/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.DataConfiguration;
import org.junit.jupiter.api.Test;

public class DataConfigurationTest {
    @Test
    public void getArrayCreatesEmptyDefaultArrayForMissingObjectProperty() {
        DataConfiguration dataConfiguration = new DataConfiguration(new BaseConfiguration());

        String[] names = (String[]) dataConfiguration.getArray(String.class, "missing.names");

        assertThat(names).isEmpty();
    }

    @Test
    public void getArrayCreatesTypedObjectArrayFromListProperty() {
        BaseConfiguration configuration = new BaseConfiguration();
        configuration.setProperty("ports", Arrays.asList("8080", "8443"));
        DataConfiguration dataConfiguration = new DataConfiguration(configuration);

        String[] ports = (String[]) dataConfiguration.getArray(String.class, "ports");

        assertThat(ports).containsExactly("8080", "8443");
    }

    @Test
    public void getArrayCreatesPrimitiveArrayFromWrapperArrayProperty() {
        RawValueConfiguration configuration = new RawValueConfiguration();
        configuration.putRaw("ports", new Integer[] {Integer.valueOf(8080), Integer.valueOf(8443)});
        DataConfiguration dataConfiguration = new DataConfiguration(configuration);

        int[] ports = (int[]) dataConfiguration.getArray(Integer.TYPE, "ports");

        assertThat(ports).containsExactly(8080, 8443);
    }

    @Test
    public void getArrayCreatesPrimitiveArrayFromCollectionProperty() {
        BaseConfiguration configuration = new BaseConfiguration();
        configuration.setProperty("feature.enabled", Arrays.asList("true", "false"));
        DataConfiguration dataConfiguration = new DataConfiguration(configuration);

        boolean[] enabled = (boolean[]) dataConfiguration.getArray(Boolean.TYPE, "feature.enabled");

        assertThat(enabled).containsExactly(true, false);
    }

    @Test
    public void getArrayCreatesSingleElementPrimitiveArrayFromScalarProperty() {
        BaseConfiguration configuration = new BaseConfiguration();
        configuration.setProperty("feature.enabled", "true");
        DataConfiguration dataConfiguration = new DataConfiguration(configuration);

        boolean[] enabled = (boolean[]) dataConfiguration.getArray(Boolean.TYPE, "feature.enabled");

        assertThat(enabled).containsExactly(true);
    }

    private static final class RawValueConfiguration extends BaseConfiguration {
        private void putRaw(String key, Object value) {
            addPropertyDirect(key, value);
        }
    }
}
