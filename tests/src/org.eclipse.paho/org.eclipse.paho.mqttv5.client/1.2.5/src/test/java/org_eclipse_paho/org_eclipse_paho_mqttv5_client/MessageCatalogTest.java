/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_mqttv5_client;

import org.eclipse.paho.mqttv5.client.internal.MessageCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageCatalogTest {
    @Test
    void resolvesAClientMessageThroughThePlatformCatalog() {
        assertThat(MessageCatalog.getMessage(1)).isNotBlank();
    }
}
