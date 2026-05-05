/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_firestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.firestore.encoding.CustomClassMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PojoBeanMapperTest {
    @Test
    void serializesBeanPropertiesFromGetterAndPublicField() {
        StandardBean bean = new StandardBean();
        bean.setName("Ada");
        bean.publicField = "published";

        Object serializedObject = CustomClassMapper.convertToPlainJavaTypes(bean);

        assertThat(serializedObject).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = (Map<String, Object>) serializedObject;
        assertThat(serialized)
                .containsEntry("name", "Ada")
                .containsEntry("publicField", "published");
    }

    @Test
    void deserializesBeanPropertiesThroughConstructorSetterAndPublicField() {
        Map<String, Object> values = new HashMap<>();
        values.put("name", "Grace");
        values.put("publicField", "visible");

        StandardBean deserialized =
                CustomClassMapper.convertToCustomClass(values, StandardBean.class, null);

        assertThat(deserialized.getName()).isEqualTo("Grace");
        assertThat(deserialized.publicField).isEqualTo("visible");
    }

    @Test
    void populatesStringDocumentIdThroughSetter() throws Exception {
        try (Firestore firestore = newFirestore()) {
            DocumentReference reference = firestore.document("widgets/string-setter-id");

            StringDocumentIdSetterBean bean =
                    CustomClassMapper.convertToCustomClass(
                            Collections.emptyMap(), StringDocumentIdSetterBean.class, reference);

            assertThat(bean.getId()).isEqualTo("string-setter-id");
        }
    }

    @Test
    void populatesDocumentReferenceDocumentIdThroughSetter() throws Exception {
        try (Firestore firestore = newFirestore()) {
            DocumentReference reference = firestore.document("widgets/reference-setter-id");

            ReferenceDocumentIdSetterBean bean =
                    CustomClassMapper.convertToCustomClass(
                            Collections.emptyMap(), ReferenceDocumentIdSetterBean.class, reference);

            assertThat(bean.getReference()).isSameAs(reference);
            assertThat(bean.getReference().getId()).isEqualTo("reference-setter-id");
        }
    }

    @Test
    void populatesStringDocumentIdPublicField() throws Exception {
        try (Firestore firestore = newFirestore()) {
            DocumentReference reference = firestore.document("widgets/string-field-id");

            StringDocumentIdFieldBean bean =
                    CustomClassMapper.convertToCustomClass(
                            Collections.emptyMap(), StringDocumentIdFieldBean.class, reference);

            assertThat(bean.id).isEqualTo("string-field-id");
        }
    }

    @Test
    void populatesDocumentReferenceDocumentIdPublicField() throws Exception {
        try (Firestore firestore = newFirestore()) {
            DocumentReference reference = firestore.document("widgets/reference-field-id");

            ReferenceDocumentIdFieldBean bean =
                    CustomClassMapper.convertToCustomClass(
                            Collections.emptyMap(), ReferenceDocumentIdFieldBean.class, reference);

            assertThat(bean.reference).isSameAs(reference);
            assertThat(bean.reference.getId()).isEqualTo("reference-field-id");
        }
    }

    private static Firestore newFirestore() {
        return FirestoreOptions.newBuilder()
                .setProjectId("test-project")
                .setEmulatorHost("localhost:8080")
                .build()
                .getService();
    }

    public static class StandardBean {
        public String publicField;
        private String name;

        public StandardBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class StringDocumentIdSetterBean {
        private String id;

        public StringDocumentIdSetterBean() {
        }

        @DocumentId
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static class ReferenceDocumentIdSetterBean {
        private DocumentReference reference;

        public ReferenceDocumentIdSetterBean() {
        }

        @DocumentId
        public DocumentReference getReference() {
            return reference;
        }

        public void setReference(DocumentReference reference) {
            this.reference = reference;
        }
    }

    public static class StringDocumentIdFieldBean {
        @DocumentId
        public String id;

        public StringDocumentIdFieldBean() {
        }
    }

    public static class ReferenceDocumentIdFieldBean {
        @DocumentId
        public DocumentReference reference;

        public ReferenceDocumentIdFieldBean() {
        }
    }
}
