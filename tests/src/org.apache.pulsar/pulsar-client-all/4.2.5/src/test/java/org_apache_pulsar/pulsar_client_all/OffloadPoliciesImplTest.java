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
    void createsSerializesAndMergesLayeredPolicies() {
        Properties configuredValues = new Properties();
        configuredValues.setProperty("managedLedgerOffloadDriver", "aws-s3");
        configuredValues.setProperty("managedLedgerOffloadBucket", "archive");
        configuredValues.setProperty("managedLedgerOffloadRegion", "eu-central-1");
        configuredValues.setProperty("managedLedgerOffloadThresholdInBytes", "2048");
        configuredValues.setProperty("managedLedgerOffloadExtraConfigstorageClass", "cold");

        OffloadPoliciesImpl configured = OffloadPoliciesImpl.create(configuredValues);
        Properties serialized = configured.toProperties();

        OffloadPoliciesImpl topic = OffloadPoliciesImpl.builder()
                .managedLedgerOffloadDriver("filesystem")
                .managedLedgerOffloadRegion("topic-region")
                .build();
        OffloadPoliciesImpl namespace = OffloadPoliciesImpl.builder()
                .managedLedgerOffloadBucket("namespace-bucket")
                .build();
        Properties broker = new Properties();
        broker.setProperty("managedLedgerOffloadReadBufferSizeInBytes", "4096");
        OffloadPoliciesImpl merged = OffloadPoliciesImpl.mergeConfiguration(topic, namespace, broker);

        assertThat(configured.driverSupported()).isTrue();
        assertThat(configured.bucketValid()).isTrue();
        assertThat(serialized.getProperty("managedLedgerOffloadDriver")).isEqualTo("aws-s3");
        assertThat(serialized.getProperty("managedLedgerOffloadExtraConfigstorageClass")).isEqualTo("cold");
        assertThat(merged).isNotNull();
        assertThat(merged.getManagedLedgerOffloadDriver()).isEqualTo("filesystem");
        assertThat(merged.getManagedLedgerOffloadRegion()).isEqualTo("topic-region");
        assertThat(merged.getManagedLedgerOffloadBucket()).isEqualTo("namespace-bucket");
        assertThat(merged.getManagedLedgerOffloadReadBufferSizeInBytes()).isEqualTo(4096);
    }
}
