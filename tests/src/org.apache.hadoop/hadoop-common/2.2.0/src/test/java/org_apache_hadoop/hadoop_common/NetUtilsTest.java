/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;

import org.apache.hadoop.net.NetUtils;
import org.junit.jupiter.api.Test;

public class NetUtilsTest {
    @Test
    void wrapExceptionPreservesConnectExceptionTypeWithAdditionalContext() {
        ConnectException originalException = new ConnectException("connection refused");

        IOException wrappedException = NetUtils.wrapException(
                "namenode.example.test",
                8020,
                "client.example.test",
                34567,
                originalException);

        assertThat(wrappedException).isInstanceOf(ConnectException.class);
        assertThat(wrappedException).isNotSameAs(originalException);
        assertThat(wrappedException).hasCause(originalException);
        assertThat(wrappedException.getMessage())
                .contains("Call From client.example.test to namenode.example.test:8020")
                .contains("connection exception")
                .contains("connection refused");
    }
}
