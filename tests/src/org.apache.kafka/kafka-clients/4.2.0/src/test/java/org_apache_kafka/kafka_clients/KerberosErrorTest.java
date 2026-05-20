/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.security.kerberos.KerberosError;
import org.junit.jupiter.api.Test;

import javax.security.sasl.SaslException;

import static org.assertj.core.api.Assertions.assertThat;

public class KerberosErrorTest {

    @Test
    void initializesKerberosErrorClassifierAndHandlesUnrelatedException() {
        assertThat(KerberosError.fromException(new SaslException("not kerberos"))).isNull();
    }
}
