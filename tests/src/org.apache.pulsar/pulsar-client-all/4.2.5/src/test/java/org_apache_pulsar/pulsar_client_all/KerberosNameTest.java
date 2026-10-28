/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.common.sasl.KerberosName;
import org.junit.jupiter.api.Test;

public class KerberosNameTest {
    @Test
    void parsesServiceHostAndRealm() {
        KerberosName name = new KerberosName("pulsar/broker.example@EXAMPLE.COM");

        assertThat(name.getServiceName()).isEqualTo("pulsar");
        assertThat(name.getHostName()).isEqualTo("broker.example");
        assertThat(name.getRealm()).isEqualTo("EXAMPLE.COM");
    }
}
