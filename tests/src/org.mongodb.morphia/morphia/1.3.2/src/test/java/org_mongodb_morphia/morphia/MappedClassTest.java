/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.EntityListeners;
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.PostPersist;
import org.mongodb.morphia.annotations.PreLoad;
import org.mongodb.morphia.annotations.PrePersist;
import org.mongodb.morphia.annotations.PreSave;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.Mapper;

import static org.assertj.core.api.Assertions.assertThat;

public class MappedClassTest {
    @BeforeEach
    void resetListenerState() {
        LifecycleListener.noArgumentInvocations = 0;
    }

    @Test
    void invokesEntityLifecycleMethodWithoutArguments() {
        Mapper mapper = new Mapper();
        MappedClass mappedClass = mapper.getMappedClass(LifecycleEntity.class);
        LifecycleEntity entity = new LifecycleEntity();
        DBObject dbObject = new BasicDBObject("phase", "original");

        DBObject returned = mappedClass.callLifecycleMethods(PrePersist.class, entity, dbObject, mapper);

        assertThat(entity.entityNoArgumentMethodCalled).isTrue();
        assertThat(returned).isSameAs(dbObject);
    }

    @Test
    void invokesEntityLifecycleMethodWithDbObjectArgument() {
        Mapper mapper = new Mapper();
        MappedClass mappedClass = mapper.getMappedClass(LifecycleEntity.class);
        LifecycleEntity entity = new LifecycleEntity();
        DBObject dbObject = new BasicDBObject("phase", "original");
        entity.entityReplacement = new BasicDBObject("phase", "entity");

        DBObject returned = mappedClass.callLifecycleMethods(PreSave.class, entity, dbObject, mapper);

        assertThat(entity.entityDbObjectArgument).isSameAs(dbObject);
        assertThat(returned).isSameAs(entity.entityReplacement);
    }

    @Test
    void invokesListenerLifecycleMethodWithoutArguments() {
        Mapper mapper = new Mapper();
        MappedClass mappedClass = mapper.getMappedClass(LifecycleEntity.class);
        LifecycleEntity entity = new LifecycleEntity();
        DBObject dbObject = new BasicDBObject("phase", "original");

        DBObject returned = mappedClass.callLifecycleMethods(PreLoad.class, entity, dbObject, mapper);

        assertThat(LifecycleListener.noArgumentInvocations).isEqualTo(1);
        assertThat(returned).isSameAs(dbObject);
    }

    @Test
    void invokesListenerLifecycleMethodWithEntityArgument() {
        Mapper mapper = new Mapper();
        MappedClass mappedClass = mapper.getMappedClass(LifecycleEntity.class);
        LifecycleEntity entity = new LifecycleEntity();
        DBObject dbObject = new BasicDBObject("phase", "original");

        DBObject returned = mappedClass.callLifecycleMethods(PostLoad.class, entity, dbObject, mapper);

        assertThat(entity.listenerEntityArgumentSeen).isTrue();
        assertThat(returned).isSameAs(dbObject);
    }

    @Test
    void invokesListenerLifecycleMethodWithEntityAndDbObjectArguments() {
        Mapper mapper = new Mapper();
        MappedClass mappedClass = mapper.getMappedClass(LifecycleEntity.class);
        LifecycleEntity entity = new LifecycleEntity();
        DBObject dbObject = new BasicDBObject("phase", "original");
        entity.listenerReplacement = new BasicDBObject("phase", "listener");

        DBObject returned = mappedClass.callLifecycleMethods(PostPersist.class, entity, dbObject, mapper);

        assertThat(entity.listenerDbObjectArgument).isSameAs(dbObject);
        assertThat(returned).isSameAs(entity.listenerReplacement);
    }

    @EntityListeners(LifecycleListener.class)
    public static class LifecycleEntity {
        private boolean entityNoArgumentMethodCalled;
        private boolean listenerEntityArgumentSeen;
        private DBObject entityDbObjectArgument;
        private DBObject listenerDbObjectArgument;
        private DBObject entityReplacement;
        private DBObject listenerReplacement;

        @PrePersist
        private void beforePersist() {
            entityNoArgumentMethodCalled = true;
        }

        @PreSave
        private DBObject beforeSave(final DBObject dbObject) {
            entityDbObjectArgument = dbObject;
            return entityReplacement;
        }
    }

    public static class LifecycleListener {
        private static int noArgumentInvocations;

        @PreLoad
        private void beforeLoad() {
            noArgumentInvocations++;
        }

        @PostLoad
        private void afterLoad(final LifecycleEntity entity) {
            entity.listenerEntityArgumentSeen = true;
        }

        @PostPersist
        private DBObject afterPersist(final LifecycleEntity entity, final DBObject dbObject) {
            entity.listenerDbObjectArgument = dbObject;
            return entity.listenerReplacement;
        }
    }
}
