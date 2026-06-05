/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.message.EndTxnResponseData;
import org.apache.kafka.test.TestUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaTestTestUtilsTest {

    @Test
    void readsDeclaredFieldValue() {
        EndTxnResponseData response = new EndTxnResponseData().setProducerId(23L);

        Long producerId = TestUtils.fieldValue(response, EndTxnResponseData.class, "producerId");

        assertThat(producerId).isEqualTo(23L);
    }

    @Test
    void updatesDeclaredFieldValue() throws Exception {
        EndTxnResponseData response = new EndTxnResponseData().setProducerId(23L);

        TestUtils.setFieldValue(response, "producerId", 42L);

        assertThat(response.producerId()).isEqualTo(42L);
    }
}
