/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseDocument;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.Document;

@Timeout(60)
public class MappingCouchbaseConverterTest {

    @Test
    void readsClassPropertiesFromStoredNames() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        mappingContext.getRequiredPersistentEntity(ClassHolder.class);
        MappingCouchbaseConverter converter = new MappingCouchbaseConverter(mappingContext);
        CouchbaseDocument types = new CouchbaseDocument()
                .setContent(Map.of("value", new StringBuilder(String.class.getName())));
        CouchbaseDocument document = new CouchbaseDocument()
                .setId("class-holder")
                .setContent(Map.of("types", types));

        ClassHolder holder = converter.read(ClassHolder.class, document);

        assertThat(holder.types.get("value")).isEqualTo(String.class);
    }

    @Document
    public static class ClassHolder {
        @Id
        public String id;
        public Map<String, Class<?>> types;
    }
}
