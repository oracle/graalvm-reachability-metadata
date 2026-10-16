/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.pulsar.common.api.proto.BrokerEntryMetadata;
import org.apache.pulsar.common.intercept.AppendBrokerTimestampMetadataInterceptor;
import org.apache.pulsar.common.intercept.AppendIndexMetadataInterceptor;
import org.apache.pulsar.common.intercept.BrokerEntryMetadataInterceptor;
import org.apache.pulsar.common.intercept.BrokerEntryMetadataUtils;
import org.junit.jupiter.api.Test;

public class BrokerEntryMetadataUtilsTest {
    @Test
    void loadsAndExecutesConfiguredInterceptors() {
        final Set<String> names = new LinkedHashSet<>();
        names.add(AppendBrokerTimestampMetadataInterceptor.class.getName());
        names.add(AppendIndexMetadataInterceptor.class.getName());

        final Set<BrokerEntryMetadataInterceptor> interceptors =
                BrokerEntryMetadataUtils.loadBrokerEntryMetadataInterceptors(
                        names, BrokerEntryMetadataUtilsTest.class.getClassLoader());
        final Set<BrokerEntryMetadataInterceptor> generic = BrokerEntryMetadataUtils.loadInterceptors(
                names, BrokerEntryMetadataUtilsTest.class.getClassLoader());

        final BrokerEntryMetadata metadata = new BrokerEntryMetadata();
        interceptors.forEach(interceptor -> interceptor.intercept(metadata));
        interceptors.forEach(interceptor -> interceptor.interceptWithNumberOfMessages(metadata, 3));

        assertThat(interceptors).hasSize(2);
        assertThat(generic).hasSize(2);
        assertThat(metadata.hasBrokerTimestamp()).isTrue();
        assertThat(metadata.getIndex()).isEqualTo(2L);
    }
}
