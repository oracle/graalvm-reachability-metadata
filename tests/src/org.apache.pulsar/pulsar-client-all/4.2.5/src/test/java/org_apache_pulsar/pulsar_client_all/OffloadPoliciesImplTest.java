/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.apache.pulsar.common.policies.data.OffloadPoliciesImpl;
import org.junit.jupiter.api.Test;

public class OffloadPoliciesImplTest {
    @Test
    void createsSerializesAndMergesPolicies() {
        final Properties configured = new Properties();
        configured.setProperty("managedLedgerOffloadDriver", "aws-s3");
        configured.setProperty("managedLedgerOffloadBucket", "archive");
        configured.setProperty("managedLedgerOffloadRegion", "eu-west-1");
        configured.setProperty("managedLedgerOffloadMaxBlockSizeInBytes", "8388608");

        final OffloadPoliciesImpl created = OffloadPoliciesImpl.create(configured);
        assertThat(created.toProperties())
                .containsEntry("managedLedgerOffloadDriver", "aws-s3")
                .containsEntry("managedLedgerOffloadBucket", "archive");

        final OffloadPoliciesImpl topic = OffloadPoliciesImpl.builder()
                .managedLedgerOffloadDriver("aws-s3")
                .managedLedgerOffloadRegion("topic-region")
                .build();
        final OffloadPoliciesImpl namespace = OffloadPoliciesImpl.builder()
                .managedLedgerOffloadBucket("namespace-bucket")
                .build();
        final Properties broker = new Properties();
        broker.setProperty(OffloadPoliciesImpl.OFFLOAD_THRESHOLD_NAME_IN_CONF_FILE, "4096");

        final OffloadPoliciesImpl merged = OffloadPoliciesImpl.mergeConfiguration(topic, namespace, broker);
        assertThat(merged.getManagedLedgerOffloadRegion()).isEqualTo("topic-region");
        assertThat(merged.getManagedLedgerOffloadBucket()).isEqualTo("namespace-bucket");
        assertThat(merged.getManagedLedgerOffloadThresholdInBytes()).isEqualTo(4096L);
    }
}
