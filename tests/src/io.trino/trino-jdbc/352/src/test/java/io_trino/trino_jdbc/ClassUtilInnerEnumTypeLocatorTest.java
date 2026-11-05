/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.annotation.JsonTypeInfo;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import java.util.EnumMap;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassUtilInnerEnumTypeLocatorTest {
    @Test
    void retainsEmptyEnumSetTypeInPolymorphicJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(EnumSet.class, PolymorphicContainer.class);
        SetEnvelope original = new SetEnvelope();
        original.stages = EnumSet.noneOf(Stage.class);

        String json = mapper.writeValueAsString(original);

        assertEquals("java.util.EnumSet<" + Stage.class.getName() + ">",
                mapper.readTree(json).get("stages").get(0).asText());
        SetEnvelope restored = mapper.readValue(json, SetEnvelope.class);
        assertEquals(original.stages, restored.stages);
        assertTrue(restored.stages.add(Stage.RUNNING));
        assertEquals(EnumSet.of(Stage.RUNNING), restored.stages);
        assertTrue(original.stages.isEmpty());
        assertEquals(restored.stages,
                mapper.readValue(mapper.writeValueAsString(restored), SetEnvelope.class).stages);
    }

    @Test
    void retainsEmptyEnumMapKeyTypeInPolymorphicJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(EnumMap.class, PolymorphicContainer.class);
        MapEnvelope original = new MapEnvelope();
        original.labels = new EnumMap<>(Stage.class);

        String json = mapper.writeValueAsString(original);

        assertEquals("java.util.EnumMap<" + Stage.class.getName() + ",java.lang.Object>",
                mapper.readTree(json).get("labels").get(0).asText());
        MapEnvelope restored = mapper.readValue(json, MapEnvelope.class);
        assertEquals(original.labels, restored.labels);
        restored.labels.put(Stage.COMPLETE, "ready");
        assertEquals("ready", restored.labels.get(Stage.COMPLETE));
        assertEquals(EnumSet.of(Stage.COMPLETE), restored.labels.keySet());
        assertTrue(original.labels.isEmpty());
        assertEquals(restored.labels,
                mapper.readValue(mapper.writeValueAsString(restored), MapEnvelope.class).labels);
    }

    public enum Stage {
        RUNNING,
        COMPLETE
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
    public abstract static class PolymorphicContainer {
    }

    public static class SetEnvelope {
        public EnumSet<Stage> stages;
    }

    public static class MapEnvelope {
        public EnumMap<Stage, String> labels;
    }
}
