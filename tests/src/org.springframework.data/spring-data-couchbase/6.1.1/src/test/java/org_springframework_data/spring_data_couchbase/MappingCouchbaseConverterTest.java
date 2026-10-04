/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseDocument;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.convert.CustomConversions;

import static org.assertj.core.api.Assertions.assertThat;

public class MappingCouchbaseConverterTest {

    @Test
    void readsClassValuedDocumentProperties() {
        CustomConversions conversions = new CustomConversions(CustomConversions.StoreConversions.NONE,
                Collections.emptyList());
        MappingCouchbaseConverter converter = new MappingCouchbaseConverter(new CouchbaseMappingContext(), null,
                conversions);
        CouchbaseDocument types = new CouchbaseDocument().put("target", String.class.getName());
        CouchbaseDocument source = new CouchbaseDocument().put("types", types);

        ClassDocument document = converter.read(ClassDocument.class, source);

        assertThat(document.types).containsEntry("target", String.class);
    }

    @Document
    public static class ClassDocument {

        public Map<String, Class<?>> types;

        public ClassDocument() {
        }
    }
}
