/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaBeanBinderInnerBeanPropertiesTest {

    @Test
    void discoversInheritedAndBooleanBeanProperties() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "cache.region", "west", "cache.enabled", "true")));

        CacheSettings settings = binder.bind("cache", CacheSettings.class).get();

        assertThat(settings.getRegion()).isEqualTo("west");
        assertThat(settings.isEnabled()).isTrue();
    }

    public static class RegionalSettings {
        private String region;

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }
    }

    public static class CacheSettings extends RegionalSettings {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
