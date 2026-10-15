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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class OffloadPoliciesImplTest {
    @Test
    void createsExportsAndMergesOffloadConfiguration() {
        Properties source = new Properties();
        source.setProperty("managedLedgerOffloadDriver", "S3");
        source.setProperty("s3ManagedLedgerOffloadRegion", "us-test-1");
        source.setProperty("s3ManagedLedgerOffloadBucket", "archive");
        source.setProperty("managedLedgerOffloadThresholdInBytes", "4096");
        source.setProperty("managedLedgerOffloadExtraConfigStorageClass", "cold");

        OffloadPoliciesImpl created = OffloadPoliciesImpl.create(source);
        Properties exported = created.toProperties();

        assertThat(created.isS3Driver()).isTrue();
        assertThat(created.bucketValid()).isTrue();
        assertThat(created.getManagedLedgerOffloadThresholdInBytes()).isEqualTo(4096L);
        assertThat(exported)
                .containsEntry("managedLedgerOffloadDriver", "S3")
                .containsEntry("s3ManagedLedgerOffloadRegion", "us-test-1")
                .containsEntry("managedLedgerOffloadExtraConfigStorageClass", "cold");

        OffloadPoliciesImpl topic = OffloadPoliciesImpl.builder()
                .managedLedgerOffloadThresholdInSeconds(30L)
                .build();
        OffloadPoliciesImpl namespace = OffloadPoliciesImpl.builder()
                .managedLedgerOffloadDriver("filesystem")
                .fileSystemURI("file:///archive")
                .build();
        Properties broker = new Properties();
        broker.setProperty("managedLedgerOffloadThresholdInBytes", "8192");

        OffloadPoliciesImpl merged =
                OffloadPoliciesImpl.mergeConfiguration(topic, namespace, broker);
        assertThat(merged).isNotNull();
        assertThat(merged.getManagedLedgerOffloadThresholdInSeconds()).isEqualTo(30L);
        assertThat(merged.getManagedLedgerOffloadDriver()).isEqualTo("filesystem");
        assertThat(merged.getFileSystemURI()).isEqualTo("file:///archive");
        assertThat(merged.getManagedLedgerOffloadThresholdInBytes()).isEqualTo(8192L);
    }
}
