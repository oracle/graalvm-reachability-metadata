/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package biz_aQute_bnd.biz_aQute_bnd_annotation;

import static org.assertj.core.api.Assertions.assertThat;

import aQute.bnd.annotation.metatype.Configurable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class ConfigurableTest {
    @Test
    void createsConfigurableWithParameterizedCollectionInterface() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ports", "8080|8081|8082");

        ServerConfiguration configuration = Configurable.createConfigurable(ServerConfiguration.class, properties);

        assertThat(configuration.ports()).isInstanceOf(ArrayList.class)
            .containsExactly(8080, 8081, 8082);
    }

    public interface ServerConfiguration {
        List<Integer> ports();
    }
}
