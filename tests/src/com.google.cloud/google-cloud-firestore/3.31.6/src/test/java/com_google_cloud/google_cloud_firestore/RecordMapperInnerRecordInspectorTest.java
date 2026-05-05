/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_firestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.firestore.encoding.CustomClassMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class RecordMapperInnerRecordInspectorTest {
    @Test
    void serializesAndDeserializesJavaRecordComponents() {
        FirestoreRecord original = new FirestoreRecord("Ada", 37, true);

        Object serializedObject = CustomClassMapper.convertToPlainJavaTypes(original);

        assertThat(serializedObject).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = (Map<String, Object>) serializedObject;
        assertThat(serialized)
                .containsEntry("name", "Ada")
                .containsEntry("score", 37)
                .containsEntry("active", true);

        FirestoreRecord deserialized =
                CustomClassMapper.convertToCustomClass(serialized, FirestoreRecord.class, null);

        assertThat(deserialized).isEqualTo(original);
    }

    public record FirestoreRecord(String name, int score, boolean active) {
    }
}
