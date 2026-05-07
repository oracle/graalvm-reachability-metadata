/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.internal.DefaultImplementation;
import org.apache.pulsar.client.internal.PulsarClientImplementationBinding;
import org.junit.jupiter.api.Test;

public class DefaultImplementationTest {
    @Test
    void loadsDefaultImplementationWithNoArgumentConstructor() {
        final PulsarClientImplementationBinding implementation = DefaultImplementation.getDefaultImplementation();

        assertThat(implementation).isNotNull();
        assertThat(implementation.getClass().getName())
                .isEqualTo("org.apache.pulsar.client.impl.PulsarClientImplementationBindingImpl");
    }
}
