/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigBeanImplTest {
    @Test
    void createsBeanWithOptionalFieldAndNestedBean() {
        Config config = ConfigFactory.parseString("""
                name = "runtime-service"
                enabled = true
                retry-count = 3
                nested {
                  endpoint = "https://example.invalid/api"
                }
                """).resolve();

        ApplicationSettings settings = ConfigBeanFactory.create(config, ApplicationSettings.class);

        assertThat(settings.getName()).isEqualTo("runtime-service");
        assertThat(settings.isEnabled()).isTrue();
        assertThat(settings.getRetryCount()).isEqualTo(3);
        assertThat(settings.getDescription()).isNull();
        assertThat(settings.getNested().getEndpoint()).isEqualTo("https://example.invalid/api");
    }

    public static class ApplicationSettings {
        private boolean enabled;
        private String name;
        private NestedSettings nested;
        private int retryCount;

        @Optional
        private String description;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public NestedSettings getNested() {
            return nested;
        }

        public void setNested(NestedSettings nested) {
            this.nested = nested;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class NestedSettings {
        private String endpoint;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }
}
