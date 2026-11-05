/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaBeanBinderInnerBeanTest {

    @Test
    void createsAndPopulatesNewJavaBeanInstance() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("app.pool.max-size", "16");
        properties.put("app.pool.idle-timeout", "45s");
        properties.put("app.pool.validation-query", "SELECT 1");
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));

        BindResult<PoolSettings> result = binder.bind("app.pool", Bindable.of(PoolSettings.class));

        assertThat(result.isBound()).isTrue();
        PoolSettings settings = result.get();
        assertThat(settings.getMaxSize()).isEqualTo(16);
        assertThat(settings.getIdleTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(settings.getValidationQuery()).isEqualTo("SELECT 1");
    }

    @Test
    void leavesJavaBeanUnboundWhenNoPropertiesMatch() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("other.value", "x")));

        BindResult<PoolSettings> result = binder.bind("app.pool", Bindable.of(PoolSettings.class));

        assertThat(result.isBound()).isFalse();
        assertThat(result.orElseGet(PoolSettings::new).getMaxSize()).isEqualTo(8);
    }

    public static class PoolSettings {

        private int maxSize = 8;

        private Duration idleTimeout = Duration.ofMinutes(1);

        private String validationQuery;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public Duration getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public String getValidationQuery() {
            return validationQuery;
        }

        public void setValidationQuery(String validationQuery) {
            this.validationQuery = validationQuery;
        }
    }
}
