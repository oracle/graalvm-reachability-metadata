/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.hk2.utilities.HK2LoaderImpl;
import org.junit.jupiter.api.Test;

public class HK2LoaderImplTest {
    @Test
    void loadsClassThroughConfiguredLoader() {
        HK2LoaderImpl loader = new HK2LoaderImpl(getClass().getClassLoader());

        Class<?> loaded = loader.loadClass("org.apache.pulsar.client.api.PulsarClient");

        assertThat(loaded.getName()).isEqualTo("org.apache.pulsar.client.api.PulsarClient");
    }
}
