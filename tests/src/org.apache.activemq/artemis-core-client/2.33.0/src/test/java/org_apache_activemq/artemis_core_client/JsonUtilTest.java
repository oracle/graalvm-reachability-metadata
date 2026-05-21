/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_core_client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.json.JsonArray;
import org.junit.jupiter.api.Test;

public class JsonUtilTest {
    @Test
    void restoresSerializedCompositeDataArraysFromJson() throws Exception {
        CompositeDataSupport source = compositeData("orders", 12);

        JsonArray jsonArray = JsonUtil.toJSONArray(new Object[] {new CompositeData[] {source}});
        Object[] values = JsonUtil.fromJsonArray(jsonArray);

        assertThat(values).hasSize(1);
        assertThat(values[0]).isInstanceOf(Map.class);
        Map<?, ?> restoredMap = (Map<?, ?>) values[0];
        assertThat(restoredMap).hasSize(1);
        assertThat(restoredMap.containsKey(CompositeData.class.getName())).isTrue();
        assertThat(restoredMap.get(CompositeData.class.getName())).isInstanceOf(CompositeData[].class);
        CompositeData[] restored = (CompositeData[]) restoredMap.get(CompositeData.class.getName());
        assertThat(restored).hasSize(1);
        assertThat(restored[0].get("queue")).isEqualTo("orders");
        assertThat(restored[0].get("messageCount")).isEqualTo(12);
        assertThat(restored[0].getCompositeType()).isEqualTo(source.getCompositeType());
    }

    @Test
    void convertsObjectArrayElementsToDesiredArrayType() {
        Object converted = JsonUtil.convertJsonValue(new Object[] {1L, 2L, 3L}, Integer.class);

        assertThat(converted).isInstanceOf(Integer[].class);
        assertThat((Integer[]) converted).containsExactly(1, 2, 3);
    }

    private static CompositeDataSupport compositeData(String queue, int messageCount) throws Exception {
        String[] itemNames = {"queue", "messageCount"};
        CompositeType compositeType = new CompositeType(
                "QueueStats",
                "Queue statistics",
                itemNames,
                itemNames,
                new OpenType<?>[] {SimpleType.STRING, SimpleType.INTEGER});
        Map<String, Object> values = new HashMap<>();
        values.put("queue", queue);
        values.put("messageCount", messageCount);
        return new CompositeDataSupport(compositeType, values);
    }
}
