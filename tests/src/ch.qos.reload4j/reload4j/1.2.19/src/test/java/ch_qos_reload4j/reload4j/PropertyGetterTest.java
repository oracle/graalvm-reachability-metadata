/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import org.apache.log4j.Level;
import org.apache.log4j.config.PropertyGetter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyGetterTest {
    @Test
    void reportsNonNullHandledBeanPropertiesThroughCallback() {
        SampleLogConfiguration configuration = new SampleLogConfiguration();
        Map<String, Object> properties = new LinkedHashMap<>();

        PropertyGetter.getProperties(configuration, (object, prefix, name, value) -> {
            assertThat(object).isSameAs(configuration);
            assertThat(prefix).isEqualTo("log.");
            properties.put(name, value);
        }, "log.");

        assertThat(properties)
                .containsEntry("name", "audit")
                .containsEntry("maxBackupIndex", 3)
                .containsEntry("maxFileSize", 1_048_576L)
                .containsEntry("immediateFlush", true)
                .containsEntry("threshold", Level.WARN)
                .doesNotContainKeys("ignoredObject", "nullValue", "class");
    }

    public static final class SampleLogConfiguration {
        public String getName() {
            return "audit";
        }

        public int getMaxBackupIndex() {
            return 3;
        }

        public long getMaxFileSize() {
            return 1_048_576L;
        }

        public boolean isImmediateFlush() {
            return true;
        }

        public Level getThreshold() {
            return Level.WARN;
        }

        public String getNullValue() {
            return null;
        }

        public Object getIgnoredObject() {
            return "ignored";
        }
    }
}
