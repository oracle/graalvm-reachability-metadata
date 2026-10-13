/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.cache.internal.NoCachingRegionFactory;
import org.hibernate.cfg.CacheSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StrategyCreatorRegionFactoryImplTest {

    private static final AtomicReference<Object> CONFIGURED_PREFIX = new AtomicReference<>();

    @Test
    void namedRegionFactoryIsConstructedWithTheConfigurationMap() {
        CONFIGURED_PREFIX.set(null);
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Lighthouse.class);
        configuration.setProperty(CacheSettings.USE_SECOND_LEVEL_CACHE, true);
        configuration.setProperty(CacheSettings.CACHE_REGION_PREFIX, "people");
        configuration.setProperty(CacheSettings.CACHE_REGION_FACTORY, MapConfiguredRegionFactory.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                session.persist(new Lighthouse(1L, "first"));
                session.persist(new Lighthouse(2L, "second"));
            });
            sessionFactory.inTransaction(session -> session.find(Lighthouse.class, 1L));

            assertThat(sessionFactory.getCache().containsEntity(Lighthouse.class, 1L)).isFalse();
        }
        assertThat(CONFIGURED_PREFIX).hasValue("people");
    }

    @Test
    void namedRegionFactoryWithoutPropertiesConstructorUsesTheDefaultConstructor() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Lighthouse.class);
        configuration.setProperty(CacheSettings.USE_SECOND_LEVEL_CACHE, true);
        configuration.setProperty(CacheSettings.CACHE_REGION_FACTORY, NoCachingRegionFactory.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                session.persist(new Lighthouse(1L, "first"));
                session.persist(new Lighthouse(2L, "second"));
            });

            assertThat(sessionFactory.getCache().containsEntity(Lighthouse.class, 2L)).isFalse();
        }
    }

    public static class MapConfiguredRegionFactory extends NoCachingRegionFactory {

        public MapConfiguredRegionFactory(Map<?, ?> properties) {
            CONFIGURED_PREFIX.set(properties.get(CacheSettings.CACHE_REGION_PREFIX));
        }
    }

    @Entity(name = "Lighthouse")
    public static class Lighthouse {

        @Id
        private Long id;

        private String label;

        public Lighthouse() {
        }

        public Lighthouse(Long id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
