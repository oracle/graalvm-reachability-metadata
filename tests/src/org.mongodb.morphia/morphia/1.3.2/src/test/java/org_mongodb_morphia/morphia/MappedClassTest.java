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
import org.mongodb.morphia.annotations.EntityListeners;
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.PostPersist;
import org.mongodb.morphia.annotations.PreLoad;
import org.mongodb.morphia.annotations.PrePersist;
import org.mongodb.morphia.annotations.PreSave;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.Mapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MappedClassTest {
    @Test
    public void invokesEntityAndListenerLifecycleCallbacksWithSupportedSignatures() {
        final Mapper mapper = new Mapper();
        final MappedClass mappedClass = mapper.getMappedClass(LifecycleEntity.class);
        final LifecycleEntity entity = new LifecycleEntity();
        final DBObject document = new BasicDBObject("source", "document");

        final DBObject prePersist = mappedClass.callLifecycleMethods(PrePersist.class, entity, document, mapper);
        final DBObject preSave = mappedClass.callLifecycleMethods(PreSave.class, entity, document, mapper);
        final DBObject preLoad = mappedClass.callLifecycleMethods(PreLoad.class, entity, document, mapper);
        mappedClass.callLifecycleMethods(PostLoad.class, entity, document, mapper);
        mappedClass.callLifecycleMethods(PostPersist.class, entity, document, mapper);

        assertThat(prePersist).isSameAs(document);
        assertThat(preSave.get("entity")).isEqualTo("preSave");
        assertThat(preLoad.get("listener")).isEqualTo("preLoad");
        assertThat(entity.events).containsExactly(
                "entity-no-args",
                "entity-with-document:document",
                "listener-with-entity-and-document:document",
                "listener-with-entity");

        final Object noArgumentListener = mapper.getInstanceCache().get(NoArgumentListener.class);
        assertThat(noArgumentListener).isInstanceOf(NoArgumentListener.class);
        assertThat(((NoArgumentListener) noArgumentListener).called).isTrue();
    }

    @Entity
    @EntityListeners({NoArgumentListener.class, EntityArgumentListener.class, EntityAndDocumentArgumentListener.class})
    public static final class LifecycleEntity {
        private final List<String> events = new ArrayList<String>();

        @PrePersist
        private void beforePersist() {
            events.add("entity-no-args");
        }

        @PreSave
        private DBObject beforeSave(final DBObject document) {
            events.add("entity-with-document:" + document.get("source"));
            return new BasicDBObject("entity", "preSave");
        }
    }

    public static final class NoArgumentListener {
        private boolean called;

        @PostLoad
        private void afterLoad() {
            called = true;
        }
    }

    public static final class EntityArgumentListener {
        @PostPersist
        private void afterPersist(final LifecycleEntity entity) {
            entity.events.add("listener-with-entity");
        }
    }

    public static final class EntityAndDocumentArgumentListener {
        @PreLoad
        private DBObject beforeLoad(final LifecycleEntity entity, final DBObject document) {
            entity.events.add("listener-with-entity-and-document:" + document.get("source"));
            return new BasicDBObject("listener", "preLoad");
        }
    }
}
