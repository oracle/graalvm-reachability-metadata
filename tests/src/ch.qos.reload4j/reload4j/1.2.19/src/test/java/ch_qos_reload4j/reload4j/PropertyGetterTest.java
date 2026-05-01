/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Priority;
import org.apache.log4j.config.PropertyGetter;
import org.junit.jupiter.api.Test;

public class PropertyGetterTest {

    @Test
    void invokesHandledBeanGettersAndReportsNonNullProperties() {
        ConfigurableTarget target = new ConfigurableTarget();
        Map<String, Object> properties = new LinkedHashMap<>();

        PropertyGetter.getProperties(target, (source, prefix, name, value) -> {
            assertThat(source).isSameAs(target);
            assertThat(prefix).isEqualTo("log4j.appender.sample.");
            properties.put(name, value);
        }, "log4j.appender.sample.");

        assertThat(properties)
                .containsEntry("name", "sample-appender")
                .containsEntry("bufferSize", 256)
                .containsEntry("timeoutMillis", 5000L)
                .containsEntry("enabled", true)
                .containsEntry("threshold", Level.WARN)
                .doesNotContainKeys("description", "metadata");
    }

    public static final class ConfigurableTarget {
        public String getName() {
            return "sample-appender";
        }

        public int getBufferSize() {
            return 256;
        }

        public long getTimeoutMillis() {
            return 5000L;
        }

        public boolean isEnabled() {
            return true;
        }

        public Priority getThreshold() {
            return Level.WARN;
        }

        public String getDescription() {
            return null;
        }

        public Map<String, String> getMetadata() {
            return Collections.singletonMap("ignored", "not a handled type");
        }
    }
}
