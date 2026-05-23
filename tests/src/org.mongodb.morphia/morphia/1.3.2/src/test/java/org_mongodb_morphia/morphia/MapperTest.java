/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.BasicDBObject;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.mapping.cache.DefaultEntityCache;

public class MapperTest {
    @Test
    void getIdReadsPrivateMappedIdField() {
        Mapper mapper = new Mapper();
        mapper.addMappedClass(IdentifiedEntity.class);
        IdentifiedEntity entity = new IdentifiedEntity("entity-id");

        Object id = mapper.getId(entity);

        assertThat(id).isEqualTo("entity-id");
    }

    @Test
    void updateKeyAndVersionInfoCopiesGeneratedIdFromDBObject() {
        Mapper mapper = new Mapper();
        mapper.addMappedClass(IdentifiedEntity.class);
        IdentifiedEntity entity = new IdentifiedEntity(null);
        BasicDBObject savedDocument = new BasicDBObject(Mapper.ID_KEY, "generated-id");

        mapper.updateKeyAndVersionInfo(null, savedDocument, new DefaultEntityCache(), entity);

        assertThat(entity.id).isEqualTo("generated-id");
    }

    @Entity(noClassnameStored = true)
    public static class IdentifiedEntity {
        @Id
        private String id;

        public IdentifiedEntity() {
        }

        IdentifiedEntity(String id) {
            this.id = id;
        }
    }
}
