/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.apache.pulsar.common.api.proto.BrokerEntryMetadata;
import org.apache.pulsar.common.intercept.AppendBrokerTimestampMetadataInterceptor;
import org.apache.pulsar.common.intercept.AppendIndexMetadataInterceptor;
import org.apache.pulsar.common.intercept.BrokerEntryMetadataInterceptor;
import org.apache.pulsar.common.intercept.BrokerEntryMetadataUtils;
import org.junit.jupiter.api.Test;

public class BrokerEntryMetadataUtilsTest {
    @Test
    void loadsAndAppliesConfiguredMetadataInterceptors() {
        Set<String> classNames = Set.of(
                AppendBrokerTimestampMetadataInterceptor.class.getName(),
                AppendIndexMetadataInterceptor.class.getName());
        Set<BrokerEntryMetadataInterceptor> interceptors =
                BrokerEntryMetadataUtils.loadBrokerEntryMetadataInterceptors(
                        classNames, getClass().getClassLoader());
        Set<BrokerEntryMetadataInterceptor> genericInterceptors =
                BrokerEntryMetadataUtils.loadInterceptors(classNames, getClass().getClassLoader());
        BrokerEntryMetadata metadata = new BrokerEntryMetadata();

        for (BrokerEntryMetadataInterceptor interceptor : interceptors) {
            interceptor.intercept(metadata);
            interceptor.interceptWithNumberOfMessages(metadata, 4);
        }

        assertThat(interceptors).hasSize(2);
        assertThat(genericInterceptors).hasSize(2);
        assertThat(metadata.hasBrokerTimestamp()).isTrue();
        assertThat(metadata.hasIndex()).isTrue();
    }
}
