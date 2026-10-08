/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_admin_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.admin.LongRunningProcessStatus;
import org.apache.pulsar.client.admin.OffloadProcessStatus;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminBuilder;
import org.apache.pulsar.client.api.MessageId;
import org.junit.jupiter.api.Test;

public class DefaultImplementationTest {
    @Test
    void adminBuilderCreatesConfigurableBuilderThroughPublicApi() {
        final PulsarAdminBuilder builder = PulsarAdmin.builder();

        assertThat(builder).isNotNull();
        assertThat(builder.serviceHttpUrl("http://127.0.0.1:8080")).isSameAs(builder);
    }

    @Test
    void offloadProcessStatusFactoryUsesItsConfiguredImplementation() {
        final OffloadProcessStatus status = OffloadProcessStatus.forError("offload failed");

        assertThat(status.getStatus()).isEqualTo(LongRunningProcessStatus.Status.ERROR);
        assertThat(status.getLastError()).isEqualTo("offload failed");
        assertThat(status.getFirstUnoffloadedMessage()).isEqualTo(MessageId.earliest);
    }
}
