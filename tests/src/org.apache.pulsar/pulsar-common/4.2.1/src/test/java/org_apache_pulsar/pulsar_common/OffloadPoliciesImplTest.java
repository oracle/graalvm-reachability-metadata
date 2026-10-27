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
    void createsPoliciesFromPropertiesAndSerializesConfiguredValues() {
        final Properties properties = new Properties();
        properties.setProperty("managedLedgerOffloadDriver", "aws-s3");
        properties.setProperty("managedLedgerOffloadBucket", "topic-bucket");
        properties.setProperty("managedLedgerOffloadRegion", "eu-west-1");
        properties.setProperty("managedLedgerOffloadServiceEndpoint", "https://storage.example.test");
        properties.setProperty("managedLedgerOffloadMaxBlockSizeInBytes", "8388608");
        properties.setProperty("managedLedgerOffloadReadBufferSizeInBytes", "1048576");
        properties.setProperty("managedLedgerOffloadExtraConfigregionHint", "primary");
        properties.setProperty(OffloadPoliciesImpl.OFFLOAD_THRESHOLD_NAME_IN_CONF_FILE, "128");
        properties.setProperty(OffloadPoliciesImpl.DELETION_LAG_NAME_IN_CONF_FILE, "256");

        final OffloadPoliciesImpl policies = OffloadPoliciesImpl.create(properties);
        final Properties serialized = policies.toProperties();

        assertThat(policies.driverSupported()).isTrue();
        assertThat(policies.bucketValid()).isTrue();
        assertThat(policies.getManagedLedgerOffloadThresholdInBytes()).isEqualTo(128L);
        assertThat(policies.getManagedLedgerOffloadDeletionLagInMillis()).isEqualTo(256L);
        assertThat(serialized.getProperty("managedLedgerOffloadDriver")).isEqualTo("aws-s3");
        assertThat(serialized.getProperty("managedLedgerOffloadBucket")).isEqualTo("topic-bucket");
        assertThat(serialized.getProperty("managedLedgerOffloadRegion")).isEqualTo("eu-west-1");
        assertThat(serialized.getProperty("managedLedgerOffloadExtraConfigregionHint")).isEqualTo("primary");
    }

    @Test
    void mergesTopicNamespaceAndBrokerOffloadConfigurationByPrecedence() {
        final OffloadPoliciesImpl topicPolicies = OffloadPoliciesImpl.builder()
                .managedLedgerOffloadDriver("aws-s3")
                .managedLedgerOffloadRegion("topic-region")
                .build();
        final OffloadPoliciesImpl namespacePolicies = OffloadPoliciesImpl.builder()
                .managedLedgerOffloadBucket("namespace-bucket")
                .managedLedgerOffloadServiceEndpoint("https://namespace.example.test")
                .build();
        final Properties brokerProperties = new Properties();
        brokerProperties.setProperty(OffloadPoliciesImpl.OFFLOAD_THRESHOLD_NAME_IN_CONF_FILE, "4096");
        brokerProperties.setProperty("managedLedgerOffloadReadBufferSizeInBytes", "2097152");

        final OffloadPoliciesImpl merged = OffloadPoliciesImpl.mergeConfiguration(
                topicPolicies, namespacePolicies, brokerProperties);

        assertThat(merged).isNotNull();
        assertThat(merged.getManagedLedgerOffloadDriver()).isEqualTo("aws-s3");
        assertThat(merged.getManagedLedgerOffloadRegion()).isEqualTo("topic-region");
        assertThat(merged.getManagedLedgerOffloadBucket()).isEqualTo("namespace-bucket");
        assertThat(merged.getManagedLedgerOffloadServiceEndpoint()).isEqualTo("https://namespace.example.test");
        assertThat(merged.getManagedLedgerOffloadThresholdInBytes()).isEqualTo(4096L);
        assertThat(merged.getManagedLedgerOffloadReadBufferSizeInBytes()).isEqualTo(2097152);
    }
}
