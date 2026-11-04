/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.event.ConfigurationEvent;
import org.apache.commons.configuration2.event.EventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Configuration change events let an order service audit live setting updates before and after they are applied.
public class ConfigurationChangeAuditTest {
    @Test
    @Timeout(60)
    public void auditsSettingChangesAndStopsAfterListenerRemoval() {
        BaseConfiguration configuration = new BaseConfiguration();
        List<String> auditTrail = new ArrayList<>();
        EventListener<ConfigurationEvent> listener = event -> {
            if ("service.timeout-seconds".equals(event.getPropertyName())) {
                String phase = event.isBeforeUpdate() ? "before" : "after";
                auditTrail.add(phase + ":" + event.getPropertyValue());
            }
        };

        configuration.addEventListener(ConfigurationEvent.ANY, listener);
        configuration.addProperty("service.timeout-seconds", 30);
        configuration.setProperty("service.timeout-seconds", 45);

        assertEquals(List.of("before:30", "after:30", "before:45", "after:45"), auditTrail);
        assertEquals(45, configuration.getInt("service.timeout-seconds"));

        assertTrue(configuration.removeEventListener(ConfigurationEvent.ANY, listener));
        configuration.setProperty("service.timeout-seconds", 60);
        assertEquals(List.of("before:30", "after:30", "before:45", "after:45"), auditTrail);
        assertEquals(60, configuration.getInt("service.timeout-seconds"));
    }
}
