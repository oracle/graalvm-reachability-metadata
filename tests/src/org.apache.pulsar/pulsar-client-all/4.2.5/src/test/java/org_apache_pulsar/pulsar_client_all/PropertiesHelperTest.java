/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.PropertiesHelper;
import org.junit.jupiter.api.Test;

public class PropertiesHelperTest {

    @Test
    void convertsTextWithAConstructorAndAValueOfFactory() {
        final TopicName topic = PropertiesHelper.convertValue("orders", TopicName.class);
        final Delivery delivery = PropertiesHelper.convertValue("STORED", Delivery.class);

        assertThat(topic.value()).isEqualTo("orders");
        assertThat(delivery).isEqualTo(Delivery.STORED);
    }

    public static final class TopicName {
        private final String value;

        public TopicName(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum Delivery {
        STORED
    }
}
