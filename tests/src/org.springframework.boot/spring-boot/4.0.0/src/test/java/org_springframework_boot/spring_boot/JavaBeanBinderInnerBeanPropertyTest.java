/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaBeanBinderInnerBeanPropertyTest {
    @Test
    void usesSettersAndUpdatesAGetterOnlyCollection() {
        Settings settings = new Settings();
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "settings.title", "production", "settings.labels[0]", "blue", "settings.labels[1]", "fast")));

        Settings bound = binder.bind("settings", Bindable.ofInstance(settings)).get();

        assertThat(bound).isSameAs(settings);
        assertThat(settings.getTitle()).isEqualTo("production");
        assertThat(settings.getLabels()).containsExactly("blue", "fast");
    }

    public static class Settings {
        private String title;
        private final List<String> labels = new ArrayList<>();

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public List<String> getLabels() {
            return labels;
        }
    }
}
