/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.cache.internal.NoCachingRegionFactory;
import org.hibernate.cache.internal.StrategyCreatorRegionFactoryImpl;
import org.hibernate.cache.spi.RegionFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class StrategyCreatorRegionFactoryImplTest {

    @Test
    public void createsRegionFactoriesUsingEachSupportedConstructor() {
        Properties properties = new Properties();
        properties.setProperty("region", "configured");
        StrategyCreatorRegionFactoryImpl creator =
                new StrategyCreatorRegionFactoryImpl(properties);

        RegionFactory propertiesFactory = creator.create(PropertiesRegionFactory.class);
        RegionFactory mapFactory = creator.create(MapRegionFactory.class);
        RegionFactory noArgFactory = creator.create(NoArgRegionFactory.class);

        assertThat(propertiesFactory).isInstanceOf(PropertiesRegionFactory.class);
        assertThat(((PropertiesRegionFactory) propertiesFactory).getRegion()).isEqualTo("configured");
        assertThat(mapFactory).isInstanceOf(MapRegionFactory.class);
        assertThat(((MapRegionFactory) mapFactory).getRegion()).isEqualTo("configured");
        assertThat(noArgFactory).isInstanceOf(NoArgRegionFactory.class);
    }

    public static class PropertiesRegionFactory extends NoCachingRegionFactory {
        private final String region;

        public PropertiesRegionFactory(Properties properties) {
            region = properties.getProperty("region");
        }

        public String getRegion() {
            return region;
        }
    }

    public static class MapRegionFactory extends NoCachingRegionFactory {
        private final String region;

        public MapRegionFactory(Map<?, ?> properties) {
            region = properties.get("region").toString();
        }

        public String getRegion() {
            return region;
        }
    }

    public static class NoArgRegionFactory extends NoCachingRegionFactory {
        public NoArgRegionFactory() {
        }
    }
}
