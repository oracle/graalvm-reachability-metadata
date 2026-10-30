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
    void discoversInheritedAndBooleanProperties() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "service.display-name", "orders", "service.enabled", "true")));
        Service service = binder.bind("service", Service.class).get();

        assertThat(service.getDisplayName()).isEqualTo("orders");
        assertThat(service.isEnabled()).isTrue();
    }

    public static class NamedService {
        private String displayName;

        public String getDisplayName() {
            return this.displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class Service extends NamedService {
        private boolean enabled;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
