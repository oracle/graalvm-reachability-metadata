/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.command.ActiveMQObjectMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ActiveMQObjectMessageTest {

    @Test
    void serializesAndRestoresItsObjectBody() throws Exception {
        ActiveMQObjectMessage message = new ActiveMQObjectMessage();
        message.setTrustAllPackages(true);
        message.setObject(new ArrayList<>(List.of("first", "second")));
        message.storeContentAndClear();

        assertThat(message.getObject()).isEqualTo(List.of("first", "second"));
    }
}
