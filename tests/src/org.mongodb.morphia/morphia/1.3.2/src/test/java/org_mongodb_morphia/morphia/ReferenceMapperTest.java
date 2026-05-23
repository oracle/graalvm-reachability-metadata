/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.DBObject;
import com.mongodb.DBRef;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Reference;
import org.mongodb.morphia.mapping.Mapper;

public class ReferenceMapperTest {
    @Test
    void toDBObjectStoresReferenceUsingReferencedEntityId() {
        Mapper mapper = new Mapper();
        mapper.addMappedClass(ReferencedEntity.class);
        mapper.addMappedClass(ReferencingEntity.class);
        ReferencedEntity referenced = new ReferencedEntity("target-id", "target name");
        ReferencingEntity entity = new ReferencingEntity("source-id", referenced);

        DBObject dbObject = mapper.toDBObject(entity);

        assertThat(dbObject.get("reference")).isInstanceOf(DBRef.class);
        DBRef reference = (DBRef) dbObject.get("reference");
        assertThat(reference.getCollectionName()).isEqualTo("referenced_entities");
        assertThat(reference.getId()).isEqualTo("target-id");
    }

    @Entity(value = "referencing_entities", noClassnameStored = true)
    public static class ReferencingEntity {
        @Id
        private String id;

        @Reference
        private ReferencedEntity reference;

        public ReferencingEntity() {
        }

        ReferencingEntity(String id, ReferencedEntity reference) {
            this.id = id;
            this.reference = reference;
        }
    }

    @Entity(value = "referenced_entities", noClassnameStored = true)
    public static class ReferencedEntity {
        @Id
        private String id;

        private String name;

        public ReferencedEntity() {
        }

        ReferencedEntity(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
