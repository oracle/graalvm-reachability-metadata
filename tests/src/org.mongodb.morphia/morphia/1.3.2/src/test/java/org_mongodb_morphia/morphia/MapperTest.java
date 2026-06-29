/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import com.mongodb.BasicDBObject;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Version;
import org.mongodb.morphia.mapping.Mapper;

import static org.assertj.core.api.Assertions.assertThat;

public class MapperTest {
    @Test
    public void readsAnnotatedIdFieldFromMappedEntity() {
        final Mapper mapper = new Mapper();
        final VersionedEntity entity = new VersionedEntity("entity-id");

        final Object id = mapper.getId(entity);

        assertThat(id).isEqualTo("entity-id");
    }

    @Test
    public void updatesKeyAndVersionFieldsFromDocument() {
        final Mapper mapper = new Mapper();
        final VersionedEntity entity = new VersionedEntity("existing-id");
        final BasicDBObject document = new BasicDBObject(Mapper.ID_KEY, "existing-id")
                .append("version", 2L);

        mapper.updateKeyAndVersionInfo(null, document, mapper.createEntityCache(), entity);

        assertThat(entity.id).isEqualTo("existing-id");
        assertThat(entity.version).isEqualTo(2L);
    }

    @Entity("mapper_versioned_entities")
    public static final class VersionedEntity {
        @Id
        private String id;
        @Version
        private Long version;

        private VersionedEntity() {
        }

        private VersionedEntity(final String id) {
            this.id = id;
        }
    }
}
