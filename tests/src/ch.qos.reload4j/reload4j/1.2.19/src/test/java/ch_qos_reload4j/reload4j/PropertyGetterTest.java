/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.TTCCLayout;
import org.apache.log4j.config.PropertyGetter;
import org.junit.jupiter.api.Test;

public class PropertyGetterTest {
    @Test
    void readsHandledBeanPropertiesThroughCallback() {
        TTCCLayout layout = new TTCCLayout();
        layout.setCategoryPrefixing(false);
        layout.setContextPrinting(false);
        layout.setThreadPrinting(true);
        Map<String, Object> properties = new HashMap<>();

        PropertyGetter.getProperties(layout, (object, prefix, name, value) -> {
            assertThat(object).isSameAs(layout);
            assertThat(prefix).isEqualTo("layout.");
            properties.put(prefix + name, value);
        }, "layout.");

        assertThat(properties)
                .containsEntry("layout.categoryPrefixing", false)
                .containsEntry("layout.contextPrinting", false)
                .containsEntry("layout.threadPrinting", true);
    }
}
