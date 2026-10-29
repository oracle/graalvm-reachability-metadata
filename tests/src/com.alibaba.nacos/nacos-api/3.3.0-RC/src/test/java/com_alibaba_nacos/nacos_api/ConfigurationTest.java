/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_api;

import com.alibaba.nacos.api.config.ConfigChangeEvent;
import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.config.GetConfigRequest;
import com.alibaba.nacos.api.config.PropertyChangeType;
import com.alibaba.nacos.api.config.PublishConfigRequest;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigurationTest {
    @Test
    void encodesConditionalConfigurationQueries() {
        GetConfigRequest request = GetConfigRequest.builder()
                .dataId("orders.properties")
                .group("payments")
                .timeoutMs(30000L)
                .localMd5("cached-content-digest")
                .build();

        GetConfigRequest restored = JsonUtils.toObj(JsonUtils.toJson(request), GetConfigRequest.class);
        assertEquals("orders.properties", restored.getDataId());
        assertEquals("payments", restored.getGroup());
        assertEquals(30000L, restored.getTimeoutMs());
        assertEquals("cached-content-digest", restored.getLocalMd5());
    }

    @Test
    void encodesCompareAndSwapConfigurationUpdates() {
        PublishConfigRequest request = PublishConfigRequest.builder()
                .dataId("orders.properties")
                .group("payments")
                .type("properties")
                .content("workers=8\nregion=east\n")
                .casMd5("previous-content-digest")
                .build();

        PublishConfigRequest restored = JsonUtils.toObj(JsonUtils.toJsonBytes(request), PublishConfigRequest.class);
        assertEquals("orders.properties", restored.getDataId());
        assertEquals("payments", restored.getGroup());
        assertEquals("properties", restored.getType());
        assertEquals("workers=8\nregion=east\n", restored.getContent());
        assertEquals("previous-content-digest", restored.getCasMd5());
    }

    @Test
    void exposesConfigurationChangesByKey() {
        ConfigChangeItem modified = new ConfigChangeItem("workers", "4", "8");
        modified.setType(PropertyChangeType.MODIFIED);
        ConfigChangeItem removed = new ConfigChangeItem("legacy", "enabled", null);
        removed.setType(PropertyChangeType.DELETED);
        ConfigChangeEvent event = new ConfigChangeEvent(Map.of("workers", modified, "legacy", removed));

        assertSame(modified, event.getChangeItem("workers"));
        assertEquals("4", event.getChangeItem("workers").getOldValue());
        assertEquals("8", event.getChangeItem("workers").getNewValue());
        assertEquals(PropertyChangeType.MODIFIED, event.getChangeItem("workers").getType());
        assertEquals(PropertyChangeType.DELETED, event.getChangeItem("legacy").getType());
        assertNull(event.getChangeItem("legacy").getNewValue());
        assertNull(event.getChangeItem("missing"));
        assertEquals(2, event.getChangeItems().size());
        assertTrue(event.getChangeItems().containsAll(List.of(modified, removed)));
    }
}
