/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.pulsar.shade.org.apache.commons.lang3.builder.ToStringStyle;
import org.junit.jupiter.api.Test;

public class ReflectionToStringBuilderTest {

    @Test
    void rendersObjectFieldsWithTheReflectionStyle() {
        final String rendered = ReflectionToStringBuilder.toString(
                new Broker("broker-a", 6650), ToStringStyle.SHORT_PREFIX_STYLE);

        assertThat(rendered).contains("Broker", "broker-a", "6650");
    }

    public static final class Broker {
        private final String name;
        private final int port;

        public Broker(String name, int port) {
            this.name = name;
            this.port = port;
        }
    }
}
