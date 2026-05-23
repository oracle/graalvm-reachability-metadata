/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.EntityListeners;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PrePersist;

public class MappedClassTest {
    @Test
    void callsEntityAndListenerLifecycleMethodsBeforeMappingToDBObject() {
        LifecycleListener.reset();
        Morphia morphia = new Morphia();
        morphia.map(LifecycleEntity.class);
        LifecycleEntity entity = new LifecycleEntity("entity-1", "stored-value");

        DBObject dbObject = morphia.toDBObject(entity);

        assertThat(entity.events).contains("entity-no-args",
                "entity-db-object",
                "listener-entity",
                "listener-entity-db-object");
        assertThat(LifecycleListener.events).contains("listener-no-args");
        assertThat(dbObject.get("_id")).isEqualTo("entity-1");
        assertThat(dbObject.get("value")).isEqualTo("stored-value");
        assertThat(dbObject.get("entityLifecycle")).isEqualTo(true);
        assertThat(dbObject.get("listenerLifecycle")).isEqualTo(true);
    }

    @Entity(noClassnameStored = true)
    @EntityListeners(LifecycleListener.class)
    public static class LifecycleEntity {
        @Id
        private String id;
        private String value;
        private final List<String> events = new ArrayList<String>();

        public LifecycleEntity() {
        }

        LifecycleEntity(String id, String value) {
            this.id = id;
            this.value = value;
        }

        @PrePersist
        private void recordEntityNoArgsLifecycleMethod() {
            events.add("entity-no-args");
        }

        @PrePersist
        private DBObject recordEntityDBObjectLifecycleMethod(DBObject dbObject) {
            events.add("entity-db-object");
            dbObject.put("entityLifecycle", true);
            return dbObject;
        }
    }

    public static class LifecycleListener {
        private static final List<String> events = new ArrayList<String>();

        public LifecycleListener() {
        }

        static void reset() {
            events.clear();
        }

        @PrePersist
        private void recordListenerNoArgsLifecycleMethod() {
            events.add("listener-no-args");
        }

        @PrePersist
        private void recordListenerEntityLifecycleMethod(LifecycleEntity entity) {
            entity.events.add("listener-entity");
        }

        @PrePersist
        private DBObject recordListenerEntityAndDBObjectLifecycleMethod(LifecycleEntity entity, DBObject dbObject) {
            entity.events.add("listener-entity-db-object");
            BasicDBObject updated = new BasicDBObject();
            updated.putAll(dbObject);
            updated.put("listenerLifecycle", true);
            return updated;
        }
    }
}
