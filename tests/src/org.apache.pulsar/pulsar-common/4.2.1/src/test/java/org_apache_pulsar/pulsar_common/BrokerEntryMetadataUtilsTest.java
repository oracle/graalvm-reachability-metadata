/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.apache.pulsar.common.api.proto.BrokerEntryMetadata;
import org.apache.pulsar.common.intercept.AppendBrokerTimestampMetadataInterceptor;
import org.apache.pulsar.common.intercept.AppendIndexMetadataInterceptor;
import org.apache.pulsar.common.intercept.BrokerEntryMetadataInterceptor;
import org.apache.pulsar.common.intercept.BrokerEntryMetadataUtils;
import org.junit.jupiter.api.Test;

public class BrokerEntryMetadataUtilsTest {
    private static final ClassLoader CLASS_LOADER = BrokerEntryMetadataUtilsTest.class.getClassLoader();

    @Test
    void loadBrokerEntryMetadataInterceptorsInstantiatesConfiguredInterceptorClasses() {
        Set<String> interceptorNames = Set.of(
                AppendBrokerTimestampMetadataInterceptor.class.getName(),
                AppendIndexMetadataInterceptor.class.getName());

        Set<BrokerEntryMetadataInterceptor> interceptors = BrokerEntryMetadataUtils.loadBrokerEntryMetadataInterceptors(
                interceptorNames, CLASS_LOADER);

        assertThat(interceptors)
                .hasSize(2)
                .extracting(Object::getClass)
                .containsExactlyInAnyOrder(
                        AppendBrokerTimestampMetadataInterceptor.class,
                        AppendIndexMetadataInterceptor.class);

        BrokerEntryMetadata metadata = new BrokerEntryMetadata();
        AppendIndexMetadataInterceptor indexInterceptor = findInterceptor(
                interceptors, AppendIndexMetadataInterceptor.class);
        indexInterceptor.interceptWithNumberOfMessages(metadata, 3);

        assertThat(metadata.getIndex()).isEqualTo(2L);
    }

    @Test
    void loadInterceptorsInstantiatesConfiguredClassesInOrder() {
        Set<String> interceptorNames = Set.of(AppendIndexMetadataInterceptor.class.getName());

        Set<BrokerEntryMetadataInterceptor> interceptors = BrokerEntryMetadataUtils.loadInterceptors(
                interceptorNames, CLASS_LOADER);

        assertThat(interceptors)
                .hasSize(1)
                .first()
                .isInstanceOf(AppendIndexMetadataInterceptor.class);

        AppendIndexMetadataInterceptor indexInterceptor = (AppendIndexMetadataInterceptor) interceptors.iterator()
                .next();
        BrokerEntryMetadata metadata = indexInterceptor.interceptWithNumberOfMessages(
                new BrokerEntryMetadata(), 5);

        assertThat(metadata.getIndex()).isEqualTo(4L);
    }

    private static <T extends BrokerEntryMetadataInterceptor> T findInterceptor(
            Set<BrokerEntryMetadataInterceptor> interceptors, Class<T> interceptorClass) {
        return interceptors.stream()
                .filter(interceptorClass::isInstance)
                .map(interceptorClass::cast)
                .findFirst()
                .orElseThrow();
    }
}
