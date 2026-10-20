/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.proto.Requests;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageV3Test {
    @Test
    void buildsSerializesAndParsesAnAvaticaProtocolMessage() throws Exception {
        Requests.OpenConnectionRequest input = Requests.OpenConnectionRequest.newBuilder()
                .setConnectionId("connection-2")
                .putInfo("user", "analyst")
                .build();

        Requests.OpenConnectionRequest output =
                Requests.OpenConnectionRequest.parseFrom(input.toByteArray());

        assertThat(output).isEqualTo(input);
        assertThat(output.getInfoOrThrow("user")).isEqualTo("analyst");
    }
}
