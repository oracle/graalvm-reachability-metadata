/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UtilsTest {

    @Test
    void createsParameterizedInstanceByClassName() throws Exception {
        ParameterizedWidget widget = Utils.newParameterizedInstance(
                ParameterizedWidget.class.getName(),
                String.class, "kafka",
                Integer.TYPE, 42);

        assertThat(widget.name()).isEqualTo("kafka");
        assertThat(widget.partition()).isEqualTo(42);
    }

    @Test
    void suggestsPublicConcreteSubclassForAbstractType() {
        assertThatThrownBy(() -> Utils.ensureConcreteSubclass(Widget.class, AbstractWidget.class))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining(AbstractWidget.ConcreteWidget.class.getName());
    }

    public interface Widget {
    }

    public abstract static class AbstractWidget implements Widget {

        public static class ConcreteWidget implements Widget {
        }
    }

    public static class ParameterizedWidget {
        private final String name;
        private final int partition;

        public ParameterizedWidget(String name, int partition) {
            this.name = name;
            this.partition = partition;
        }

        public String name() {
            return name;
        }

        public int partition() {
            return partition;
        }
    }
}
