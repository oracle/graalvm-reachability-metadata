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

public class MessageLiteToStringTest {
    @Test
    void rendersPopulatedProtocolMessageFields() {
        Requests.OpenConnectionRequest request = Requests.OpenConnectionRequest.newBuilder()
                .setConnectionId("connection-1")
                .putInfo("project", "sales")
                .build();

        assertThat(request.toString()).contains("connection_id", "connection-1", "project", "sales");
    }
}
