/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.mapping.cache.DefaultEntityCache;

import static org.assertj.core.api.Assertions.assertThat;

public class MapperTest {
    @Test
    void readsIdFieldFromMappedEntity() {
        Mapper mapper = new Mapper();
        MapperIdEntity entity = new MapperIdEntity("existing-id");

        Object id = mapper.getId(entity);

        assertThat(id).isEqualTo("existing-id");
    }

    @Test
    void updatesIdFieldFromDbObjectAfterSave() {
        Mapper mapper = new Mapper();
        MapperIdEntity entity = new MapperIdEntity();
        DBObject dbObject = new BasicDBObject(Mapper.ID_KEY, "generated-id");

        mapper.updateKeyAndVersionInfo(null, dbObject, new DefaultEntityCache(), entity);

        assertThat(entity.id()).isEqualTo("generated-id");
    }

    @Entity("mapper_id_entities")
    public static class MapperIdEntity {
        @Id
        private String id;

        MapperIdEntity() {
        }

        MapperIdEntity(final String id) {
            this.id = id;
        }

        String id() {
            return id;
        }
    }
}
