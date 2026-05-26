/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.cfg.spi.DomainDataRegionBuildingContext;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.spi.AbstractRegionFactory;
import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.QueryResultsRegion;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.TimestampsRegion;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class StrategyCreatorRegionFactoryImplTest {

    private static final String MARKER_SETTING = "strategy.creator.region.factory.marker";
    private static final String PROPERTIES_MARKER = "properties-constructor";
    private static final String MAP_MARKER = "map-constructor";
    private static final String EMPTY_MARKER = "empty-constructor";

    private static final AtomicReference<Properties> propertiesConstructorArgument = new AtomicReference<>();
    private static final AtomicReference<Map<?, ?>> mapConstructorArgument = new AtomicReference<>();
    private static final AtomicInteger emptyConstructorInvocations = new AtomicInteger();

    @Test
    public void shouldCreateRegionFactoryUsingPropertiesConstructor() {
        propertiesConstructorArgument.set(null);

        RegionFactory regionFactory = resolveRegionFactory(PropertiesConstructorRegionFactory.class, PROPERTIES_MARKER);

        assertThat(regionFactory).isInstanceOf(PropertiesConstructorRegionFactory.class);
        assertThat(propertiesConstructorArgument.get()).containsEntry(MARKER_SETTING, PROPERTIES_MARKER);
    }

    @Test
    public void shouldCreateRegionFactoryUsingMapConstructor() {
        mapConstructorArgument.set(null);

        RegionFactory regionFactory = resolveRegionFactory(MapConstructorRegionFactory.class, MAP_MARKER);

        assertThat(regionFactory).isInstanceOf(MapConstructorRegionFactory.class);
        assertThat(mapConstructorArgument.get().get(MARKER_SETTING)).isEqualTo(MAP_MARKER);
    }

    @Test
    public void shouldCreateRegionFactoryUsingEmptyConstructor() {
        emptyConstructorInvocations.set(0);

        RegionFactory regionFactory = resolveRegionFactory(EmptyConstructorRegionFactory.class, EMPTY_MARKER);

        assertThat(regionFactory).isInstanceOf(EmptyConstructorRegionFactory.class);
        assertThat(emptyConstructorInvocations.get()).isEqualTo(1);
    }

    private static RegionFactory resolveRegionFactory(
            Class<? extends RegionFactory> regionFactoryClass,
            String marker) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings(regionFactoryClass, marker))
                .build();
        try {
            return registry.getService(RegionFactory.class);
        }
        finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static Map<String, Object> settings(Class<? extends RegionFactory> regionFactoryClass, String marker) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.CACHE_REGION_FACTORY, regionFactoryClass);
        settings.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, true);
        settings.put(MARKER_SETTING, marker);
        return settings;
    }

    public static class PropertiesConstructorRegionFactory extends BaseRegionFactory {
        public PropertiesConstructorRegionFactory(Properties properties) {
            propertiesConstructorArgument.set(properties);
        }
    }

    public static class MapConstructorRegionFactory extends BaseRegionFactory {
        public MapConstructorRegionFactory(Map<?, ?> settings) {
            mapConstructorArgument.set(settings);
        }
    }

    public static class EmptyConstructorRegionFactory extends BaseRegionFactory {
        public EmptyConstructorRegionFactory() {
            emptyConstructorInvocations.incrementAndGet();
        }
    }

    public abstract static class BaseRegionFactory extends AbstractRegionFactory {
        @Override
        protected void prepareForUse(SessionFactoryOptions settings, Map<String, Object> configValues) {
        }

        @Override
        protected void releaseFromUse() {
        }

        @Override
        public DomainDataRegion buildDomainDataRegion(
                DomainDataRegionConfig regionConfig,
                DomainDataRegionBuildingContext buildingContext) {
            throw new UnsupportedOperationException("Domain data regions are not used by this test");
        }

        @Override
        public QueryResultsRegion buildQueryResultsRegion(
                String regionName,
                SessionFactoryImplementor sessionFactory) {
            throw new UnsupportedOperationException("Query result regions are not used by this test");
        }

        @Override
        public TimestampsRegion buildTimestampsRegion(
                String regionName,
                SessionFactoryImplementor sessionFactory) {
            throw new UnsupportedOperationException("Timestamp regions are not used by this test");
        }
    }
}
