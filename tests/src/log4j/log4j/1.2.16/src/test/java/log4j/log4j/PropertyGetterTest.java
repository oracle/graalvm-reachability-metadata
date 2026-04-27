/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import org.apache.log4j.Level;
import org.apache.log4j.config.PropertyGetter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyGetterTest {

    @Test
    void collectsHandledBeanPropertiesAndSkipsUnsupportedOnes() {
        SampleBean bean = new SampleBean("configured-name", true, Level.WARN, new Object());
        Map<String, Object> collectedProperties = new LinkedHashMap<>();

        PropertyGetter.getProperties(bean, (source, prefix, name, value) -> {
            collectedProperties.put(prefix + name, value);
            assertThat(source).isSameAs(bean);
        }, "bean.");

        assertThat(collectedProperties)
                .containsEntry("bean.name", "configured-name")
                .containsEntry("bean.enabled", true)
                .containsEntry("bean.priority", Level.WARN)
                .doesNotContainKey("bean.unsupported");
    }

    public static final class SampleBean {
        private final String name;
        private final boolean enabled;
        private final Level priority;
        private final Object unsupported;

        public SampleBean(String name, boolean enabled, Level priority, Object unsupported) {
            this.name = name;
            this.enabled = enabled;
            this.priority = priority;
            this.unsupported = unsupported;
        }

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Level getPriority() {
            return priority;
        }

        public Object getUnsupported() {
            return unsupported;
        }
    }
}
