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
import org.apache.pulsar.common.intercept.BrokerEntryMetadataInterceptor;
import org.apache.pulsar.common.intercept.BrokerEntryMetadataUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class BrokerEntryMetadataUtilsTest {
    @Test
    void loadsTypedAndGenericInterceptorsByConfiguredClassName() {
        ClassLoader classLoader = getClass().getClassLoader();
        Set<BrokerEntryMetadataInterceptor> brokerInterceptors =
                BrokerEntryMetadataUtils.loadBrokerEntryMetadataInterceptors(
                        Set.of(CountingInterceptor.class.getName()), classLoader);
        Set<GenericInterceptor> genericInterceptors = BrokerEntryMetadataUtils.loadInterceptors(
                Set.of(GenericInterceptor.class.getName()), classLoader);

        BrokerEntryMetadata metadata = new BrokerEntryMetadata();
        BrokerEntryMetadata updated = brokerInterceptors.iterator().next()
                .interceptWithNumberOfMessages(metadata, 5);

        assertThat(updated).isSameAs(metadata);
        assertThat(updated.getIndex()).isEqualTo(5);
        assertThat(genericInterceptors)
                .singleElement()
                .extracting(GenericInterceptor::name)
                .isEqualTo("generic");
    }

    public static final class CountingInterceptor implements BrokerEntryMetadataInterceptor {
        public CountingInterceptor() { }

        @Override
        public BrokerEntryMetadata intercept(BrokerEntryMetadata brokerMetadata) {
            return brokerMetadata;
        }

        @Override
        public BrokerEntryMetadata interceptWithNumberOfMessages(
                BrokerEntryMetadata brokerMetadata, int numberOfMessages) {
            brokerMetadata.setIndex(numberOfMessages);
            return brokerMetadata;
        }
    }

    public static final class GenericInterceptor {
        public GenericInterceptor() { }

        public String name() {
            return "generic";
        }
    }
}
