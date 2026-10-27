/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import com.mongodb.DBObject;
import com.mongodb.DBRef;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Reference;
import org.mongodb.morphia.mapping.Mapper;

import static org.assertj.core.api.Assertions.assertThat;

public class ReferenceMapperTest {
    @Test
    void writesSingleReferenceFromEntityIdField() {
        Mapper mapper = new Mapper();
        ReferenceMapperChild child = new ReferenceMapperChild("child-id");
        ReferenceMapperParent parent = new ReferenceMapperParent("parent-id", child);

        DBObject dbObject = mapper.toDBObject(parent);

        assertThat(dbObject.get("child")).isInstanceOf(DBRef.class);
        DBRef reference = (DBRef) dbObject.get("child");
        assertThat(reference.getCollectionName()).isEqualTo("reference_mapper_children");
        assertThat(reference.getId()).isEqualTo("child-id");
    }

    @Entity("reference_mapper_parents")
    public static class ReferenceMapperParent {
        @Id
        private String id;

        @Reference
        private ReferenceMapperChild child;

        public ReferenceMapperParent() {
        }

        ReferenceMapperParent(final String id, final ReferenceMapperChild child) {
            this.id = id;
            this.child = child;
        }
    }

    @Entity("reference_mapper_children")
    public static class ReferenceMapperChild {
        @Id
        private String id;

        public ReferenceMapperChild() {
        }

        ReferenceMapperChild(final String id) {
            this.id = id;
        }
    }
}
