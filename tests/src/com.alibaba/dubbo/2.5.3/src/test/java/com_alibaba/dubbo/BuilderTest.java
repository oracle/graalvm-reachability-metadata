/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class BuilderTest {
    @Test
    void roundTripsSerializableValueAfterDiscoveringDeclaredFields() throws Exception {
        DiscoveredFieldsValue source = new DiscoveredFieldsValue("saved", "temporary");

        DiscoveredFieldsValue copy =
                (DiscoveredFieldsValue) SerializationTestSupport.roundTrip("dubbo", source);

        assertThat(copy.payload).isEqualTo("saved");
        assertThat(copy.temporary).isNull();
    }

    @Test
    void roundTripsSerializableValueUsingConfiguredFieldDiscovery() throws Exception {
        ConfiguredFieldsValue source = new ConfiguredFieldsValue("configured", "temporary");

        ConfiguredFieldsValue copy =
                (ConfiguredFieldsValue) SerializationTestSupport.roundTrip("dubbo", source);

        assertThat(copy.payload).isEqualTo("configured");
        assertThat(copy.temporary).isNull();
    }

    public static class DiscoveredFieldsValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private String payload;
        private transient String temporary;

        public DiscoveredFieldsValue() { }

        DiscoveredFieldsValue(String payload, String temporary) {
            this.payload = payload;
            this.temporary = temporary;
        }
    }

    public static class ConfiguredFieldsValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private String payload;
        private transient String temporary;

        public ConfiguredFieldsValue() { }

        ConfiguredFieldsValue(String payload, String temporary) {
            this.payload = payload;
            this.temporary = temporary;
        }
    }
}
