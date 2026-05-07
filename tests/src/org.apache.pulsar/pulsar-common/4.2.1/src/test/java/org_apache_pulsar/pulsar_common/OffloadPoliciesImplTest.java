/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.apache.pulsar.common.policies.data.OffloadPoliciesImpl;
import org.junit.jupiter.api.Test;

public class OffloadPoliciesImplTest {
    @Test
    void createFromPropertiesSetsConfiguredFieldsAndExtraConfigurations() {
        Properties properties = new Properties();
        properties.setProperty("managedLedgerOffloadDriver", "aws-s3");
        properties.setProperty("managedLedgerOffloadMaxThreads", "7");
        properties.setProperty("managedLedgerOffloadThresholdInBytes", "1048576");
        properties.setProperty("managedLedgerOffloadThresholdInSeconds", "90");
        properties.setProperty("managedLedgerOffloadDeletionLagInMillis", "3000");
        properties.setProperty("managedLedgerOffloadBucket", "configured-bucket");
        properties.setProperty("managedLedgerOffloadRegion", "us-west-2");
        properties.setProperty("managedLedgerOffloadReadBufferSizeInBytes", "4096");
        properties.setProperty("managedLedgerOffloadExtraConfigcompression", "zstd");

        OffloadPoliciesImpl policies = OffloadPoliciesImpl.create(properties);

        assertThat(policies.getManagedLedgerOffloadDriver()).isEqualTo("aws-s3");
        assertThat(policies.getManagedLedgerOffloadMaxThreads()).isEqualTo(7);
        assertThat(policies.getManagedLedgerOffloadThresholdInBytes()).isEqualTo(1_048_576L);
        assertThat(policies.getManagedLedgerOffloadThresholdInSeconds()).isEqualTo(90L);
        assertThat(policies.getManagedLedgerOffloadDeletionLagInMillis()).isEqualTo(3_000L);
        assertThat(policies.getManagedLedgerOffloadBucket()).isEqualTo("configured-bucket");
        assertThat(policies.getManagedLedgerOffloadRegion()).isEqualTo("us-west-2");
        assertThat(policies.getManagedLedgerOffloadReadBufferSizeInBytes()).isEqualTo(4_096);
        assertThat(policies.getManagedLedgerExtraConfigurations()).containsEntry("compression", "zstd");
    }

    @Test
    void toPropertiesExportsRegularAndExtraConfigurationFields() {
        OffloadPoliciesImpl policies = OffloadPoliciesImpl.builder()
                .managedLedgerOffloadDriver("filesystem")
                .managedLedgerOffloadBucket("archive-bucket")
                .managedLedgerOffloadRegion("local")
                .managedLedgerOffloadThresholdInBytes(2_048L)
                .managedLedgerOffloadThresholdInSeconds(120L)
                .managedLedgerOffloadDeletionLagInMillis(6_000L)
                .managedLedgerOffloadMaxBlockSizeInBytes(8192)
                .managedLedgerOffloadReadBufferSizeInBytes(1024)
                .build();
        policies.getManagedLedgerExtraConfigurations().put("segment.format", "compact");

        Properties exportedProperties = policies.toProperties();

        assertThat(exportedProperties)
                .containsEntry("managedLedgerOffloadDriver", "filesystem")
                .containsEntry("managedLedgerOffloadBucket", "archive-bucket")
                .containsEntry("managedLedgerOffloadRegion", "local")
                .containsEntry("managedLedgerOffloadThresholdInBytes", "2048")
                .containsEntry("managedLedgerOffloadThresholdInSeconds", "120")
                .containsEntry("managedLedgerOffloadDeletionLagInMillis", "6000")
                .containsEntry("managedLedgerOffloadMaxBlockSizeInBytes", "8192")
                .containsEntry("managedLedgerOffloadReadBufferSizeInBytes", "1024")
                .containsEntry("managedLedgerOffloadExtraConfigsegment.format", "compact");
    }

    @Test
    void mergeConfigurationPrefersTopicThenNamespaceThenBrokerProperties() {
        OffloadPoliciesImpl topicPolicies = OffloadPoliciesImpl.builder()
                .managedLedgerOffloadThresholdInBytes(10L)
                .managedLedgerOffloadBucket("topic-bucket")
                .build();
        OffloadPoliciesImpl namespacePolicies = OffloadPoliciesImpl.builder()
                .managedLedgerOffloadDriver("google-cloud-storage")
                .managedLedgerOffloadRegion("namespace-region")
                .managedLedgerOffloadThresholdInBytes(20L)
                .managedLedgerOffloadThresholdInSeconds(30L)
                .build();
        Properties brokerProperties = new Properties();
        brokerProperties.setProperty("managedLedgerOffloadServiceEndpoint", "https://broker.example.invalid");
        brokerProperties.setProperty("managedLedgerOffloadReadBufferSizeInBytes", "512");

        OffloadPoliciesImpl mergedPolicies = OffloadPoliciesImpl.mergeConfiguration(
                topicPolicies, namespacePolicies, brokerProperties);

        assertThat(mergedPolicies).isNotNull();
        assertThat(mergedPolicies.getManagedLedgerOffloadThresholdInBytes()).isEqualTo(10L);
        assertThat(mergedPolicies.getManagedLedgerOffloadBucket()).isEqualTo("topic-bucket");
        assertThat(mergedPolicies.getManagedLedgerOffloadDriver()).isEqualTo("google-cloud-storage");
        assertThat(mergedPolicies.getManagedLedgerOffloadRegion()).isEqualTo("namespace-region");
        assertThat(mergedPolicies.getManagedLedgerOffloadThresholdInSeconds()).isEqualTo(30L);
        assertThat(mergedPolicies.getManagedLedgerOffloadServiceEndpoint())
                .isEqualTo("https://broker.example.invalid");
        assertThat(mergedPolicies.getManagedLedgerOffloadReadBufferSizeInBytes()).isEqualTo(512);
    }
}
