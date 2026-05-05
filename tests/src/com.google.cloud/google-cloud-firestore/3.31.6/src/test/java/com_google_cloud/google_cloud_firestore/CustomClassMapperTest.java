/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_firestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.firestore.annotation.PropertyName;
import com.google.cloud.firestore.encoding.CustomClassMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CustomClassMapperTest {
    @Test
    void serializesEnumUsingPropertyNameAnnotation() {
        Object serialized = CustomClassMapper.convertToPlainJavaTypes(WorkflowState.READY);

        assertThat(serialized).isEqualTo("ready-state");
    }

    @Test
    void deserializesEnumUsingPropertyNameAnnotation() {
        WorkflowState deserialized =
                CustomClassMapper.convertToCustomClass("ready-state", WorkflowState.class, null);

        assertThat(deserialized).isEqualTo(WorkflowState.READY);
    }

    @Test
    void deserializesParameterizedConcreteListAndMapFields() {
        Map<String, Object> values = new HashMap<>();
        values.put("names", List.of("Ada", "Grace"));
        values.put("scores", Map.of("Ada", 1, "Grace", 2));

        ParameterizedCollectionsBean deserialized =
                CustomClassMapper.convertToCustomClass(
                        values, ParameterizedCollectionsBean.class, null);

        assertThat(deserialized.names)
                .isInstanceOf(ArrayList.class)
                .containsExactly("Ada", "Grace");
        assertThat(deserialized.scores)
                .isInstanceOf(HashMap.class)
                .containsEntry("Ada", 1)
                .containsEntry("Grace", 2);
    }

    public enum WorkflowState {
        @PropertyName("ready-state")
        READY,
        DONE
    }

    public static class ParameterizedCollectionsBean {
        public ArrayList<String> names;
        public HashMap<String, Integer> scores;

        public ParameterizedCollectionsBean() {
        }
    }
}
