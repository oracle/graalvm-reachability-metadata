/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

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
    void loadsBrokerEntryMetadataInterceptorsByClassName() {
        final Set<String> interceptorNames = interceptorNames();

        final Set<BrokerEntryMetadataInterceptor> interceptors =
                BrokerEntryMetadataUtils.loadBrokerEntryMetadataInterceptors(
                        interceptorNames, BrokerEntryMetadataUtilsTest.class.getClassLoader());

        assertThat(interceptors)
                .hasSize(2)
                .extracting(Object::getClass)
                .containsExactlyInAnyOrder(
                        AppendBrokerTimestampMetadataInterceptor.class,
                        AppendIndexMetadataInterceptor.class);

        final BrokerEntryMetadata metadata = new BrokerEntryMetadata();
        interceptors.forEach(interceptor -> interceptor.intercept(metadata));
        interceptors.forEach(interceptor -> interceptor.interceptWithNumberOfMessages(metadata, 3));

        assertThat(metadata.hasBrokerTimestamp()).isTrue();
        assertThat(metadata.hasIndex()).isTrue();
        assertThat(metadata.getIndex()).isEqualTo(2L);
    }

    @Test
    void loadsGenericInterceptorsByClassNameInConfigurationOrder() {
        final Set<String> interceptorNames = interceptorNames();

        final Set<BrokerEntryMetadataInterceptor> interceptors = BrokerEntryMetadataUtils.loadInterceptors(
                interceptorNames, BrokerEntryMetadataUtilsTest.class.getClassLoader());

        assertThat(interceptors)
                .hasSize(2)
                .extracting(Object::getClass)
                .containsExactly(
                        AppendBrokerTimestampMetadataInterceptor.class,
                        AppendIndexMetadataInterceptor.class);
    }

    private static Set<String> interceptorNames() {
        final Set<String> interceptorNames = new LinkedHashSet<>();
        interceptorNames.add(AppendBrokerTimestampMetadataInterceptor.class.getName());
        interceptorNames.add(AppendIndexMetadataInterceptor.class.getName());
        return interceptorNames;
    }
}
