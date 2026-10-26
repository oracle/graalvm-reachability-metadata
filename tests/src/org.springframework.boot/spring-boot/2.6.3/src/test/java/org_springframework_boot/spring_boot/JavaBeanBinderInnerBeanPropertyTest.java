/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaBeanBinderInnerBeanPropertyTest {

    @Test
    void bindsSettersAndMergesIntoValuesExposedByGetters() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("app.cluster.name", "primary");
        properties.put("app.cluster.labels.zone", "eu-west-1a");
        properties.put("app.cluster.labels.tier", "gold");
        properties.put("app.cluster.connection.timeout-seconds", "30");
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));

        ClusterSettings settings = new ClusterSettings();
        ClusterSettings bound = binder.bind("app.cluster", Bindable.ofInstance(settings)).get();

        assertThat(bound).isSameAs(settings);
        assertThat(settings.getName()).isEqualTo("primary");
        assertThat(settings.getLabels())
                .containsEntry("zone", "eu-west-1a")
                .containsEntry("tier", "gold")
                .containsEntry("owner", "platform");
        assertThat(settings.getConnection().getTimeoutSeconds()).isEqualTo(30);
    }

    public static class ClusterSettings {

        private final Map<String, String> labels = new LinkedHashMap<>();

        private final Connection connection = new Connection();

        private String name;

        public ClusterSettings() {
            labels.put("owner", "platform");
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public Connection getConnection() {
            return connection;
        }
    }

    public static class Connection {

        private int timeoutSeconds;

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
