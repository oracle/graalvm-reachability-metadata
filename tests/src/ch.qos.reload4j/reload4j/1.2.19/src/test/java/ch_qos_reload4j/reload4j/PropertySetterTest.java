/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import org.apache.log4j.config.PropertySetter;
import org.apache.log4j.spi.OptionHandler;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertySetterTest {
    @Test
    void configuresNestedOptionHandlerPropertyFromProperties() {
        RecordingTarget target = new RecordingTarget();
        Properties properties = new Properties();
        properties.setProperty("target.policy", RecordingPolicy.class.getName());
        properties.setProperty("target.policy.name", "audit-policy");
        properties.setProperty("target.policy.bufferSize", "32");

        PropertySetter.setProperties(target, properties, "target.");

        assertThat(target.policy).isNotNull();
        assertThat(target.policy.name).isEqualTo("audit-policy");
        assertThat(target.policy.bufferSize).isEqualTo(32);
        assertThat(target.policy.activated).isTrue();
    }

    public static final class RecordingTarget {
        private RecordingPolicy policy;

        public RecordingPolicy getPolicy() {
            return policy;
        }

        public void setPolicy(RecordingPolicy policy) {
            this.policy = policy;
        }
    }

    public static final class RecordingPolicy implements OptionHandler {
        private String name;
        private int bufferSize;
        private boolean activated;

        public void setName(String name) {
            this.name = name;
        }

        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        @Override
        public void activateOptions() {
            activated = true;
        }
    }
}
